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

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.ipc.netty.tcp.InetSocketAddressUtil;
import reactor.ipc.netty.tcp.ProxyProvider;

/**
 * @author Violeta Georgieva
 * @deprecated
 */
@Deprecated
public class ClientProxyOptions {

	/**
	 * Creates a builder for {@link ClientProxyOptions ClientProxyOptions}
	 *
	 * @return a new ClientProxyOptions builder
	 */
	public static ClientProxyOptions.TypeSpec builder() {
		return new ClientProxyOptions.Build();
	}

	/**
	 * Proxy Type
	 */
	public enum Proxy {
		HTTP, SOCKS4, SOCKS5
	}

	static final class Build implements TypeSpec, AddressSpec, Builder {
		String username;
		Function<? super String, ? extends String> password;
		String host;
		int port;
		Supplier<? extends InetSocketAddress> address;
		String nonProxyHosts;
		Proxy type;

		@Override
		public final Builder username(String username) {
			this.username = username;
			return this;
		}

		@Override
		public final Builder password(Function<? super String, ? extends String> password) {
			this.password = password;
			return this;
		}

		@Override
		public final Builder host(String host) {
			this.host = Objects.requireNonNull(host, "host");
			return this;
		}

		@Override
		public final Builder port(int port) {
			this.port = Objects.requireNonNull(port, "port");
			return this;
		}

		@Override
		public final Builder address(InetSocketAddress address) {
			Objects.requireNonNull(address, "address");
			this.address = () -> InetSocketAddressUtil.replaceWithResolved(address);
			return this;
		}

		@Override
		public final Builder address(Supplier<? extends InetSocketAddress> addressSupplier) {
			this.address = Objects.requireNonNull(addressSupplier, "addressSupplier");
			return this;
		}

		@Override
		public final Builder nonProxyHosts(String nonProxyHostsPattern) {
			this.nonProxyHosts = nonProxyHostsPattern;
			return this;
		}

		@Override
		public final AddressSpec type(Proxy type) {
			this.type = Objects.requireNonNull(type, "type");
			return this;
		}

		public void build(ProxyProvider.TypeSpec spec) {
			if (type.equals(Proxy.HTTP)) {
				spec.type(ProxyProvider.Proxy.HTTP);
			}
			else if (type.equals(Proxy.SOCKS4)) {
				spec.type(ProxyProvider.Proxy.SOCKS4);
			}
			else {
				spec.type(ProxyProvider.Proxy.SOCKS5);
			}

			if (host != null) {
				((ProxyProvider.AddressSpec) spec).host(host);
			}
			if (address != null) {
				((ProxyProvider.AddressSpec) spec).address(address);
			}

			if (port != 0) {
				((ProxyProvider.Builder) spec).port(port);
			}
			if (username != null) {
				((ProxyProvider.Builder) spec).username(username);
			}
			if (password != null) {
				((ProxyProvider.Builder) spec).password(password);
			}
			if (nonProxyHosts != null) {
				((ProxyProvider.Builder) spec).nonProxyHosts(nonProxyHosts);
			}
		}
	}

	public interface TypeSpec {

		/**
		 * The proxy type.
		 *
		 * @param type The proxy type.
		 * @return {@code this}
		 */
		public AddressSpec type(Proxy type);
	}

	public interface AddressSpec {

		/**
		 * The proxy host to connect to.
		 *
		 * @param host The proxy host to connect to.
		 * @return {@code this}
		 */
		public Builder host(String host);

		/**
		 * The address to connect to.
		 *
		 * @param address The address to connect to.
		 * @return {@code this}
		 */
		public Builder address(InetSocketAddress address);

		/**
		 * The supplier for the address to connect to.
		 *
		 * @param addressSupplier The supplier for the address to connect to.
		 * @return {@code this}
		 */
		public Builder address(Supplier<? extends InetSocketAddress> addressSupplier);
	}

	public interface Builder {

		/**
		 * The proxy username.
		 *
		 * @param username The proxy username.
		 * @return {@code this}
		 */
		public Builder username(String username);

		/**
		 * A function to supply the proxy's password from the username.
		 *
		 * @param password A function to supply the proxy's password from the username.
		 * @return {@code this}
		 */
		public Builder password(Function<? super String, ? extends String> password);

		/**
		 * The proxy port to connect to.
		 *
		 * @param port The proxy port to connect to.
		 * @return {@code this}
		 */
		public Builder port(int port);

		/**
		 * Regular expression (<code>using java.util.regex</code>) for a configured
		 * list of hosts that should be reached directly, bypassing the proxy.
		 *
		 * @param nonProxyHostsPattern Regular expression (<code>using java.util.regex</code>)
		 * for a configured list of hosts that should be reached directly, bypassing the proxy.
		 * @return {@code this}
		 */
		public Builder nonProxyHosts(String nonProxyHostsPattern);
	}
}