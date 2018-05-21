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

import java.net.URI;
import java.util.List;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.Type;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * netty HttpClient客户端请求执行过滤器
 * 与NettyWriteResponseFilter配合完成对路由服务的调用
 * @author Spencer Gibb
 * @author Biju Kunjummen
 */
public class NettyRoutingFilter implements GlobalFilter, Ordered {

	private final HttpClient httpClient;
	private final ObjectProvider<List<HttpHeadersFilter>> headersFilters;

	public NettyRoutingFilter(HttpClient httpClient,
			ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
		this.httpClient = httpClient;
		this.headersFilters = headersFilters;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equals(scheme) && !"https".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		//获取当前请求信息
		ServerHttpRequest request = exchange.getRequest();

		//获取当前请求方法类型
		final HttpMethod method = HttpMethod.valueOf(request.getMethod().toString());
		//获取路由的URI信息
		final String url = requestUrl.toString();

		//获取请求的header信息
		HttpHeaders filtered = filterRequest(this.headersFilters.getIfAvailable(),
				exchange);

		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		filtered.forEach(httpHeaders::set);

		//获取header TRANSFER_ENCODING
		String transferEncoding = request.getHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING);
		boolean chunkedTransfer = "chunked".equalsIgnoreCase(transferEncoding);

		boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);

		//通过httpClient发送请求信息
		return this.httpClient.request(method, url, req -> {
			//构建HttpClientRequest 请求
			final HttpClientRequest proxyRequest = req.options(NettyPipeline.SendOptions::flushOnEach)
					.headers(httpHeaders)
					.chunkedTransfer(chunkedTransfer)
					.failOnServerError(false)
					.failOnClientError(false);

			//设置代理的host信息
			if (preserveHost) {
				String host = request.getHeaders().getFirst(HttpHeaders.HOST);
				proxyRequest.header(HttpHeaders.HOST, host);
			}

			//发送请求
			return proxyRequest.sendHeaders() //I shouldn't need this
					.send(request.getBody().map(dataBuffer ->
							((NettyDataBuffer)dataBuffer).getNativeBuffer()));
		}).doOnNext(res -> {
			//获取请求的原始响应信息
			ServerHttpResponse response = exchange.getResponse();
			//构建响应header
			// put headers and status so filters can modify the response
			HttpHeaders headers = new HttpHeaders();
			//从res响应中读取header信息构建新的返回响应header
			res.responseHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));

			HttpHeaders filteredResponseHeaders = HttpHeadersFilter.filter(
					this.headersFilters.getIfAvailable(), headers, exchange, Type.RESPONSE);
			//添加header到请求原始响应中
			response.getHeaders().putAll(filteredResponseHeaders);
			//获取res响应的响应状态
			HttpStatus status = HttpStatus.resolve(res.status().code());
			//设置原始响应的状态
			if (status != null) {
				response.setStatusCode(status);
			} else if (response instanceof AbstractServerHttpResponse) {
				// https://jira.spring.io/browse/SPR-16748
				((AbstractServerHttpResponse) response).setStatusCodeValue(res.status().code());
			} else {
				throw new IllegalStateException("Unable to set status code on response: " +res.status().code()+", "+response.getClass());
			}

			//将httpClient客户端响应添加到请求的上下问环境中
			// Defer committing the response until all route filters have run
			// Put client response as ServerWebExchange attribute and write response later NettyWriteResponseFilter
			exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
		}).then(chain.filter(exchange));
	}
}
