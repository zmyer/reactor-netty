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
package reactor.ipc.netty.http.client;

import reactor.ipc.netty.options.ClientOptions;
import reactor.ipc.netty.options.ClientProxyOptions;
import reactor.ipc.netty.tcp.TcpClient;

import java.util.function.Function;

/**
 * @author Violeta Georgieva
 * @deprecated
 */
@Deprecated
public class HttpClientOptions {

	/**
	 * Create a new HttpClientOptions.Builder
	 *
	 * @return a new HttpClientOptions.Builder
	 */
	public static HttpClientOptions.Builder builder() {
		return new HttpClientOptions.Builder();
	}

	public static final class Builder extends ClientOptions.Builder<Builder> {
		private boolean acceptGzip;

		/**
		 * Enable GZip accept-encoding header and support for compressed response
		 *
		 * @param enabled true whether gzip support is enabled
		 * @return {@code this}
		 */
		public final Builder compression(boolean enabled) {
			this.acceptGzip = enabled;
			return get();
		}

		/**
		 * The HTTP proxy configuration
		 *
		 * @param proxyOptions the HTTP proxy configuration
		 * @return {@code this}
		 */
		public final Builder httpProxy(Function<ClientProxyOptions.AddressSpec, ClientProxyOptions.Builder> proxyOptions) {
			super.proxy(t -> proxyOptions.apply(t.type(ClientProxyOptions.Proxy.HTTP)));
			return get();
		}

		public HttpClient buildHttpClient() {
			TcpClient tcpClient = super.buildTcpClient();
			HttpClient httpClient = HttpClient.from(tcpClient);
			if (acceptGzip) {
				httpClient = httpClient.compress();
			}
			else {
				httpClient = httpClient.noCompression();
			}
			return httpClient;
		}
	}
}
