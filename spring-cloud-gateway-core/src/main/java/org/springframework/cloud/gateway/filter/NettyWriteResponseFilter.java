/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClientResponse;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;

/**
 * netty HttpClient客户端响应报文写入原始响应的过滤器
 * @author Spencer Gibb
 */
public class NettyWriteResponseFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(NettyWriteResponseFilter.class);

	public static final int WRITE_RESPONSE_FILTER_ORDER = -1;

	private final List<MediaType> streamingMediaTypes;

	public NettyWriteResponseFilter(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	@Override
	public int getOrder() {
		return WRITE_RESPONSE_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// NOTICE: nothing in "pre" filter stage as CLIENT_RESPONSE_ATTR is not added
		// until the WebHandler is run
		return chain.filter(exchange).then(Mono.defer(() -> {
			//获取上下文中的HttpClient 响应信息
			HttpClientResponse clientResponse = exchange.getAttribute(CLIENT_RESPONSE_ATTR);

			if (clientResponse == null) {
				return Mono.empty();
			}
			log.trace("NettyWriteResponseFilter start");
			//获取请求的原始响应
			ServerHttpResponse response = exchange.getResponse();

			//获取数据工厂
			NettyDataBufferFactory factory = (NettyDataBufferFactory) response.bufferFactory();
			//TODO: what if it's not netty

			//获取HttpClient 响应信息报文数据
			final Flux<NettyDataBuffer> body = clientResponse.receive()
					.retain() //TODO: needed?
					.map(factory::wrap);

			//获取媒体类型
			MediaType contentType = response.getHeaders().getContentType();

			//将响应报文写入到原始响应中
			return (isStreamingMediaType(contentType) ?
					response.writeAndFlushWith(body.map(Flux::just)) : response.writeWith(body));
		}));
	}

	//TODO: use framework if possible
	//TODO: port to WebClientWriteResponseFilter
	private boolean isStreamingMediaType(@Nullable MediaType contentType) {
		return (contentType != null && this.streamingMediaTypes.stream()
						.anyMatch(contentType::isCompatibleWith));
	}

}
