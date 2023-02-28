package io.moquette.spring;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.ObjectUtil;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import java.util.List;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/27 17:27
 */
public class ReloadableSslContext extends SslContext {

	private volatile SslContext ctx;

	public ReloadableSslContext(SslContext ctx) {
		this.ctx = ObjectUtil.checkNotNull(ctx, "ctx");
	}

	@Override
	public synchronized final boolean isClient() {
		return ctx.isClient();
	}

	@Override
	public synchronized final List<String> cipherSuites() {
		return ctx.cipherSuites();
	}

	@Override
	public synchronized final long sessionCacheSize() {
		return ctx.sessionCacheSize();
	}

	@Override
	public synchronized final long sessionTimeout() {
		return ctx.sessionTimeout();
	}

	@Override
	public synchronized final ApplicationProtocolNegotiator applicationProtocolNegotiator() {
		return ctx.applicationProtocolNegotiator();
	}

	@Override
	public synchronized SSLEngine newEngine(ByteBufAllocator alloc) {
		return ctx.newEngine(alloc);
	}

	@Override
	public synchronized SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
		return ctx.newEngine(alloc, peerHost, peerPort);
	}

	@Override
	public synchronized SSLSessionContext sessionContext() {
		return ctx.sessionContext();
	}

	/**
	 * 替换为新的sslContext
	 * @param newContext 新的sslContext
	 */
	public synchronized void reload(SslContext newContext) {
		synchronized (ctx) {
			ctx = newContext;
		}
	}
}
