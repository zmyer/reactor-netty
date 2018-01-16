/*
 * Copyright (c) 2011-2018 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.ipc.netty.options;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import reactor.core.Exceptions;
import reactor.ipc.netty.Connection;
import reactor.ipc.netty.resources.LoopResources;
import reactor.ipc.netty.resources.PoolResources;
import reactor.ipc.netty.tcp.InetSocketAddressUtil;
import reactor.ipc.netty.tcp.TcpClient;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Violeta Georgieva
 * @deprecated
 */
@Deprecated
public class ClientOptions {

	/**
	 * Creates a builder for {@link ClientOptions ClientOptions}
	 *
	 * @param <BUILDER> A ClientOptions.Builder subclass
	 * @return a new ClientOptions builder
	 */
	public static <BUILDER extends ClientOptions.Builder<BUILDER>> ClientOptions.Builder<BUILDER> builder() {
		return new ClientOptions.Builder<>();
	}

	public static class Builder<BUILDER extends Builder<BUILDER>> implements Supplier<BUILDER> {

		private static final boolean DEFAULT_NATIVE;

		static {
			// reactor.ipc.netty.epoll should be deprecated in favor of reactor.ipc.netty.native
			String defaultNativeEpoll =
					System.getProperty("reactor.ipc.netty.epoll");

			String defaultNative =
					System.getProperty("reactor.netty.native");

			if (defaultNative != null) {
				DEFAULT_NATIVE = Boolean.parseBoolean(defaultNative);
			}
			else if (defaultNativeEpoll != null) {
				DEFAULT_NATIVE = Boolean.parseBoolean(defaultNativeEpoll);
			}
			else {
				DEFAULT_NATIVE = true;
			}
		}

		private Map<AttributeKey, Object> attributes = new HashMap<>();
		private Map<ChannelOption, Object> options = new HashMap<>();
		private boolean preferNative = DEFAULT_NATIVE;
		private LoopResources loopResources;
		private ChannelGroup channelGroup;
		private SslContext sslContext;
		private long sslHandshakeTimeoutMillis;
		private long sslCloseNotifyFlushTimeoutMillis;
		private long sslCloseNotifyReadTimeoutMillis;
		private Consumer<? super Channel> afterChannelInit;
		private Consumer<? super Connection> afterNettyContextInit;
		private Predicate<? super Channel> onChannelInit;
		private PoolResources poolResources;
		private boolean poolDisabled = false;
		//private InternetProtocolFamily protocolFamily;
		private String host;
		private int port = -1;
		private Supplier<? extends SocketAddress> connectAddress;
		private ClientProxyOptions.Build proxyOptions;
		private AddressResolverGroup<?> resolver;

		/**
		 * Attribute default attribute to the future {@link Channel} connection.
		 *
		 * @param key the attribute key
		 * @param value the attribute value
		 * @param <T> the attribute type
		 * @return {@code this}
		 * @see Bootstrap#attr(AttributeKey, Object)
		 */
		public <T> BUILDER attr(AttributeKey<T> key, T value) {
			Objects.requireNonNull(key, "key");
			if (value == null) {
				attributes.remove(key);
			}
			else {
				attributes.put(key, value);
			}
			return get();
		}

		/**
		 * Set a {@link ChannelOption} value for low level connection settings like
		 * SO_TIMEOUT or SO_KEEPALIVE. This will apply to each new channel from remote
		 * peer.
		 *
		 * @param key the option key
		 * @param value the option value
		 * @param <T> the option type
		 * @return {@code this}
		 * @see Bootstrap#option(ChannelOption, Object)
		 */
		public <T> BUILDER option(ChannelOption<T> key, T value) {
			Objects.requireNonNull(key, "key");
			if (value == null) {
				options.remove(key);
			}
			else {
				options.put(key, value);
			}
			return get();
		}

		/**
		 * Set the preferred native option. Determine if epoll/kqueue should be used if available.
		 *
		 * @param preferNative Should the connector prefer native (epoll/kqueue) if available
		 * @return {@code this}
		 */
		public final BUILDER preferNative(boolean preferNative) {
			this.preferNative = preferNative;
			return get();
		}

		/**
		 * Provide an {@link EventLoopGroup} supplier.
		 * Note that server might call it twice for both their selection and io loops.
		 *
		 * @param channelResources a selector accepting native runtime expectation and
		 * returning an eventLoopGroup
		 * @return {@code this}
		 */
		public final BUILDER loopResources(LoopResources channelResources) {
			this.loopResources = Objects.requireNonNull(channelResources, "loopResources");
			return get();
		}

		public final boolean isLoopAvailable() {
			return this.loopResources != null;
		}

		/**
		 * Provide a shared {@link EventLoopGroup} each Connector handler.
		 *
		 * @param eventLoopGroup an eventLoopGroup to share
		 * @return {@code this}
		 */
		public final BUILDER eventLoopGroup(EventLoopGroup eventLoopGroup) {
			Objects.requireNonNull(eventLoopGroup, "eventLoopGroup");
			return loopResources(preferNative -> eventLoopGroup);
		}

		/**
		 * Provide a {@link ChannelGroup} for each active remote channel will be held in the
		 * provided group.
		 *
		 * @param channelGroup a {@link ChannelGroup} to monitor remote channel
		 * @return {@code this}
		 */
		public final BUILDER channelGroup(ChannelGroup channelGroup) {
			this.channelGroup = Objects.requireNonNull(channelGroup, "channelGroup");
			//the channelGroup being set, afterChannelInit will be augmented to add
			//each channel to the group, when actual Options are constructed
			return get();
		}

		/**
		 * Set the options to use for configuring SSL. Setting this to {@code null} means
		 * don't use SSL at all (the default).
		 *
		 * @param sslContext The context to set when configuring SSL
		 * @return {@code this}
		 */
		public final BUILDER sslContext(SslContext sslContext) {
			this.sslContext = sslContext;
			return get();
		}

		/**
		 * Set the options to use for configuring SSL handshake timeout. Default to 10000 ms.
		 *
		 * @param sslHandshakeTimeout The timeout {@link Duration}
		 * @return {@code this}
		 */
		public final BUILDER sslHandshakeTimeout(Duration sslHandshakeTimeout) {
			Objects.requireNonNull(sslHandshakeTimeout, "sslHandshakeTimeout");
			return sslHandshakeTimeoutMillis(sslHandshakeTimeout.toMillis());
		}

		/**
		 * Set the options to use for configuring SSL handshake timeout. Default to 10000 ms.
		 *
		 * @param sslHandshakeTimeoutMillis The timeout in milliseconds
		 * @return {@code this}
		 */
		public final BUILDER sslHandshakeTimeoutMillis(long sslHandshakeTimeoutMillis) {
			if(sslHandshakeTimeoutMillis < 0L){
				throw new IllegalArgumentException("ssl handshake timeout must be positive," +
						" was: "+sslHandshakeTimeoutMillis);
			}
			this.sslHandshakeTimeoutMillis = sslHandshakeTimeoutMillis;
			return get();
		}

		/**
		 * Set the options to use for configuring SSL close_notify flush timeout. Default to 3000 ms.
		 *
		 * @param sslCloseNotifyFlushTimeout The timeout {@link Duration}
		 *
		 * @return {@code this}
		 */
		public final BUILDER sslCloseNotifyFlushTimeout(Duration sslCloseNotifyFlushTimeout) {
			Objects.requireNonNull(sslCloseNotifyFlushTimeout, "sslCloseNotifyFlushTimeout");
			return sslCloseNotifyFlushTimeoutMillis(sslCloseNotifyFlushTimeout.toMillis());
		}


		/**
		 * Set the options to use for configuring SSL close_notify flush timeout. Default to 3000 ms.
		 *
		 * @param sslCloseNotifyFlushTimeoutMillis The timeout in milliseconds
		 *
		 * @return {@code this}
		 */
		public final BUILDER sslCloseNotifyFlushTimeoutMillis(long sslCloseNotifyFlushTimeoutMillis) {
			if (sslCloseNotifyFlushTimeoutMillis < 0L) {
				throw new IllegalArgumentException("ssl close_notify flush timeout must be positive," +
						" was: " + sslCloseNotifyFlushTimeoutMillis);
			}
			this.sslCloseNotifyFlushTimeoutMillis = sslCloseNotifyFlushTimeoutMillis;
			return get();
		}


		/**
		 * Set the options to use for configuring SSL close_notify read timeout. Default to 0 ms.
		 *
		 * @param sslCloseNotifyReadTimeout The timeout {@link Duration}
		 *
		 * @return {@code this}
		 */
		public final BUILDER sslCloseNotifyReadTimeout(Duration sslCloseNotifyReadTimeout) {
			Objects.requireNonNull(sslCloseNotifyReadTimeout, "sslCloseNotifyReadTimeout");
			return sslCloseNotifyFlushTimeoutMillis(sslCloseNotifyReadTimeout.toMillis());
		}


		/**
		 * Set the options to use for configuring SSL close_notify read timeout. Default to 0 ms.
		 *
		 * @param sslCloseNotifyReadTimeoutMillis The timeout in milliseconds
		 *
		 * @return {@code this}
		 */
		public final BUILDER sslCloseNotifyReadTimeoutMillis(long sslCloseNotifyReadTimeoutMillis) {
			if (sslCloseNotifyReadTimeoutMillis < 0L) {
				throw new IllegalArgumentException("ssl close_notify read timeout must be positive," +
						" was: " + sslCloseNotifyReadTimeoutMillis);
			}
			this.sslCloseNotifyReadTimeoutMillis = sslCloseNotifyReadTimeoutMillis;
			return get();
		}

		/**
		 * Setup a callback called after each {@link Channel} initialization, once
		 * reactor-netty pipeline handlers have been registered.
		 *
		 * @param afterChannelInit the post channel setup handler
		 * @return {@code this}
		 * @see #onChannelInit(Predicate)
		 * @see #afterNettyContextInit(Consumer)
		 */
		public final BUILDER afterChannelInit(Consumer<? super Channel> afterChannelInit) {
			this.afterChannelInit = Objects.requireNonNull(afterChannelInit, "afterChannelInit");
			return get();
		}

		/**
		 * Setup a {@link Predicate} for each {@link Channel} initialization that can be
		 * used to prevent the Channel's registration.
		 *
		 * @param onChannelInit predicate to accept or reject the newly created Channel
		 * @return {@code this}
		 * @see #afterChannelInit(Consumer)
		 * @see #afterNettyContextInit(Consumer)
		 */
		public final BUILDER onChannelInit(Predicate<? super Channel> onChannelInit) {
			this.onChannelInit = Objects.requireNonNull(onChannelInit, "onChannelInit");
			return get();
		}

		/**
		 * Setup a callback called after each {@link Channel} initialization, once the
		 * reactor-netty pipeline handlers have been registered and the {@link Connection}
		 * is available.
		 *
		 * @param afterNettyContextInit the post channel setup handler
		 * @return {@code this}
		 * @see #onChannelInit(Predicate)
		 * @see #afterChannelInit(Consumer)
		 */
		public final BUILDER afterNettyContextInit(Consumer<? super Connection> afterNettyContextInit) {
			this.afterNettyContextInit = Objects.requireNonNull(afterNettyContextInit, "afterNettyContextInit");
			return get();
		}

		/**
		 * Assign an {@link AddressResolverGroup}.
		 *
		 * @param resolver the new {@link AddressResolverGroup}
		 * @return {@code this}
		 */
		public final BUILDER resolver(AddressResolverGroup<?> resolver) {
			this.resolver = Objects.requireNonNull(resolver, "resolver");
			return get();
		}

		/**
		 * Configures the {@link ChannelPool} selector for the socket. Will effectively
		 * enable client connection-pooling.
		 *
		 * @param poolResources the {@link PoolResources} given
		 * an {@link InetSocketAddress}
		 * @return {@code this}
		 */
		public final BUILDER poolResources(PoolResources poolResources) {
			this.poolResources = Objects.requireNonNull(poolResources, "poolResources");
			this.poolDisabled = false;
			return get();
		}

		/**
		 * Disable current {@link #poolResources}
		 *
		 * @return {@code this}
		 */
		public BUILDER disablePool() {
			this.poolResources = null;
			this.poolDisabled = true;
			return get();
		}

		public final boolean isPoolDisabled() {
			return poolDisabled;
		}

		public final boolean isPoolAvailable() {
			return this.poolResources != null;
		}

		/** TODO
		 * Configures the version family for the socket.
		 *
		 * @param protocolFamily the version family for the socket, or null for the system
		 * default family
		 * @return {@code this}
		public final BUILDER protocolFamily(InternetProtocolFamily protocolFamily) {
			this.protocolFamily = Objects.requireNonNull(protocolFamily, "protocolFamily");
			return get();
		}
		 */

		/**
		 * Enable default sslContext support
		 *
		 * @return {@code this}
		 */
		public final BUILDER sslSupport() {
			return sslSupport(c -> {
			});
		}

		/**
		 * Enable default sslContext support and enable further customization via the passed
		 * configurator. The builder will then produce the {@link SslContext} to be passed to
		 * {@link #sslContext(SslContext)}.
		 *
		 * @param configurator builder callback for further customization.
		 * @return {@code this}
		 */
		public final BUILDER sslSupport(Consumer<? super SslContextBuilder> configurator) {
			Objects.requireNonNull(configurator, "configurator");
			try {
				SslContextBuilder builder = SslContextBuilder.forClient();
				configurator.accept(builder);
				return sslContext(builder.build());
			}
			catch (Exception sslException) {
				throw Exceptions.bubble(sslException);
			}
		}

		/**
		 * The host to which this client should connect.
		 *
		 * @param host The host to connect to.
		 * @return {@code this}
		 */
		public final BUILDER host(String host) {
			if (Objects.isNull(host)) {
				this.host = NetUtil.LOCALHOST.getHostAddress();
			}
			else {
				this.host = host;
			}
			return get();
		}

		/**
		 * The port to which this client should connect.
		 *
		 * @param port The port to connect to.
		 * @return {@code this}
		 */
		public final BUILDER port(int port) {
			this.port = port;
			return get();
		}

		/**
		 * The address to which this client should connect.
		 *
		 * @param connectAddressSupplier A supplier of the address to connect to.
		 * @return {@code this}
		 */
		public final BUILDER connectAddress(Supplier<? extends SocketAddress> connectAddressSupplier) {
			this.connectAddress = Objects.requireNonNull(connectAddressSupplier, "connectAddressSupplier");
			return get();
		}

		/**
		 * The proxy configuration
		 *
		 * @param proxyOptions the proxy configuration
		 * @return {@code this}
		 */
		public final BUILDER proxy(Function<ClientProxyOptions.TypeSpec, ClientProxyOptions.Builder> proxyOptions) {
			Objects.requireNonNull(proxyOptions, "proxyOptions");
			this.proxyOptions = (ClientProxyOptions.Build) proxyOptions.apply(ClientProxyOptions.builder());
			return get();
		}

		@SuppressWarnings("unchecked")
		@Override
		public BUILDER get() {
			return (BUILDER) this;
		}

		@SuppressWarnings("unchecked")
		public TcpClient buildTcpClient() {
			TcpClient tcpClient;
			if (isPoolDisabled()) {
				tcpClient = TcpClient.newConnection();
			}
			else if (poolResources != null){
				tcpClient = TcpClient.create(poolResources);
			}
			else {
				tcpClient = TcpClient.create();
			}

			if (connectAddress == null) {
				if (port >= 0) {
					if (host == null) {
						this.connectAddress = () -> new InetSocketAddress(NetUtil.LOCALHOST, port);
					}
					else {
						this.connectAddress = () -> InetSocketAddressUtil.createUnresolved(host, port);
					}
				}
			}
			if (connectAddress != null) {
				tcpClient = tcpClient.addressSupplier(connectAddress);
			}

			for (Map.Entry<AttributeKey, Object> entry: attributes.entrySet()) {
				tcpClient = tcpClient.attr(entry.getKey(), entry.getValue());
			}

			for (Map.Entry<ChannelOption, Object> entry: options.entrySet()) {
				tcpClient = tcpClient.option(entry.getKey(), entry.getValue());
			}

			if (proxyOptions != null) {
				tcpClient = tcpClient.proxy(t -> proxyOptions.build(t));
			}

			if (resolver != null) {
				tcpClient = tcpClient.bootstrap(bootstrap -> bootstrap.resolver(resolver));
			}

			if (sslContext != null) {
				tcpClient = tcpClient.secure(spec -> {
					spec.sslContext(sslContext);
					if (sslHandshakeTimeoutMillis != 0) {
						((Builder) spec).sslHandshakeTimeoutMillis(sslHandshakeTimeoutMillis);
					}
					if (sslCloseNotifyFlushTimeoutMillis != 0) {
						((Builder) spec).sslCloseNotifyFlushTimeoutMillis(sslCloseNotifyFlushTimeoutMillis);
					}
					if (sslCloseNotifyReadTimeoutMillis != 0) {
						((Builder) spec).sslCloseNotifyReadTimeoutMillis(sslCloseNotifyReadTimeoutMillis);
					}
				});
			}

			if (loopResources != null) {
				tcpClient = tcpClient.runOn(loopResources, preferNative);
			}

			if (afterNettyContextInit != null) {
				tcpClient = tcpClient.doOnConnected(connection -> afterNettyContextInit.accept(connection));
			}

			if (onChannelInit != null) {
				tcpClient = tcpClient.doOnConnected(connection -> {
					if (onChannelInit.test(connection.channel())) {
						connection.dispose();
						// TODO
						//fireContextError(new AbortedException("Channel has been dropped"));
					}
				});
			}

			Consumer<? super Channel> afterChannel = afterChannelInit;
			if (afterChannel != null && channelGroup != null) {
				this.afterChannelInit = ((Consumer<Channel>) channelGroup::add)
						.andThen(afterChannel);
			}
			else if (afterChannel != null) {
				this.afterChannelInit = afterChannel;
			}
			else if (channelGroup != null) {
				this.afterChannelInit = channelGroup::add;
			}
			if (afterChannelInit != null) {
				tcpClient = tcpClient.doOnConnected(connection -> afterChannelInit.accept(connection.channel()));
			}

			return tcpClient;
		}
	}
}
