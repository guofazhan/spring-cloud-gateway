/*
 * Copyright 2013-2017 the original author or authors.
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
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;

import reactor.core.publisher.Mono;

/**
 * 路由的路径转换过滤器
 * @author Spencer Gibb
 */
public class RouteToRequestUrlFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(RouteToRequestUrlFilter.class);

	public static final int ROUTE_TO_URL_FILTER_ORDER = 10000;
	private static final String SCHEME_REGEX = "[a-zA-Z]([a-zA-Z]|\\d|\\+|\\.|-)*:.*";
	static final Pattern schemePattern = Pattern.compile(SCHEME_REGEX);

	@Override
	public int getOrder() {
		return ROUTE_TO_URL_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		//获取上下文路由信息
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		if (route == null) {
			return chain.filter(exchange);
		}
		log.trace("RouteToRequestUrlFilter start");
		//获取请求URI信息
		URI uri = exchange.getRequest().getURI();
		boolean encoded = containsEncodedParts(uri);
		//获取路由RUI信息
		URI routeUri = route.getUri();

		if (hasAnotherScheme(routeUri)) {
			// this is a special url, save scheme to special attribute
			// replace routeUri with schemeSpecificPart
			//将路由的URI资源定位标识写入到上下文信息中
			exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, routeUri.getScheme());
			routeUri = URI.create(routeUri.getSchemeSpecificPart());
		}

		//重新构建路由的URI信息
		URI requestUrl = UriComponentsBuilder.fromUri(uri)
				.uri(routeUri)
				.build(encoded)
				.toUri();
		//设置路由URI信息到上下文环境中
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		return chain.filter(exchange);
	}

	/* for testing */ static boolean hasAnotherScheme(URI uri) {
		return schemePattern.matcher(uri.getSchemeSpecificPart()).matches() && uri.getHost() == null
				&& uri.getRawPath() == null;
	}
}
