package io.moquette.integration.mqtt5;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.integration.IntegrationUtils;
import io.moquette.testclient.Client;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ConnectTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectTest.class);

    Server broker;
    IConfig config;

    @TempDir
    Path tempFolder;
    private String dbPath;
    private Client lowLevelClient;

    protected void startServer(String dbPath) throws IOException {
        broker = new Server();
        final Properties configProps = IntegrationUtils.prepareTestProperties(dbPath);
        config = new MemoryConfig(configProps);
        broker.startServer(config);
    }

    @BeforeAll
    public static void beforeTests() {
        Awaitility.setDefaultTimeout(Durations.ONE_SECOND);
    }

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = IntegrationUtils.tempH2Path(tempFolder);
        startServer(dbPath);

        lowLevelClient = new Client("localhost").clientId("subscriber");
    }

    @AfterEach
    public void tearDown() throws Exception {
        stopServer();
    }

    private void stopServer() {
        broker.stopServer();
    }

    @Test
    public void simpleConnect() {
        Mqtt5BlockingClient client = MqttClient.builder()
            .useMqttVersion5()
            .identifier("simple_connect_test")
            .serverHost("localhost")
            .serverPort(1883)
            .buildBlocking();
        final Mqtt5ConnAck connectAck = client.connect();
        assertEquals(Mqtt5ConnAckReasonCode.SUCCESS, connectAck.getReasonCode(), "Accept plain connection");

        client.disconnect();
    }

    @Test
    public void sendConnectOnDisconnectedConnection() {
        MqttConnAckMessage connAck = lowLevelClient.connectV5();
        assertConnectionAccepted(connAck, "Connection must be accepted");
        lowLevelClient.disconnect();

        try {
            lowLevelClient.connectV5();
            fail("Connect on Disconnected TCP socket can't happen");
        } catch (RuntimeException rex) {
            assertEquals("Cannot receive ConnAck in 200 ms", rex.getMessage());
        }
    }

    @Test
    public void receiveInflightPublishesAfterAReconnect() {
        final Mqtt5BlockingClient publisher = MqttClient.builder()
            .useMqttVersion5()
            .identifier("publisher")
            .serverHost("localhost")
            .serverPort(1883)
            .buildBlocking();
        Mqtt5ConnAck connectAck = publisher.connect();
        assertEquals(Mqtt5ConnAckReasonCode.SUCCESS, connectAck.getReasonCode(), "Publisher connected");

        final MqttConnAckMessage connAck = lowLevelClient.connectV5();
        assertConnectionAccepted(connAck, "Connection must be accepted");
        lowLevelClient.subscribe("/test", MqttQoS.AT_LEAST_ONCE);

        final Mqtt5PublishResult pubResult = publisher.publishWith()
            .topic("/test")
            .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
            .payload("Hello".getBytes(StandardCharsets.UTF_8))
            .send();
        assertFalse(pubResult.getError().isPresent(), "Publisher published");

        lowLevelClient.disconnect();

        // reconnect the raw subscriber
        final Client reconnectingSubscriber = new Client("localhost").clientId("subscriber");
        assertConnectionAccepted(reconnectingSubscriber.connectV5(), "Connection must be accepted");

        Awaitility.await()
            .atMost(2, TimeUnit.SECONDS)
            .until(reconnectingSubscriber::hasReceivedMessages);

        final String publishPayload = reconnectingSubscriber.nextQueuedMessage()
            .filter(m -> m instanceof MqttPublishMessage)
            .map(m -> (MqttPublishMessage) m)
            .map(m -> m.payload().toString(StandardCharsets.UTF_8))
            .orElse("Fake Payload");
        assertEquals("Hello", publishPayload, "The inflight payload from previous subscription MUST be received");

        reconnectingSubscriber.disconnect();
    }

    private void assertConnectionAccepted(MqttConnAckMessage connAck, String message) {
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, connAck.variableHeader().connectReturnCode(), message);
    }
}
