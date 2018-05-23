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

import reactor.core.publisher.Mono;

import java.net.URI;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

/**
 * 路径转发过滤器
 * Filter to set the path in the request URI if the {@link Route} URI has the scheme
 * <code>forward</code>.
 * @author Ryan Baxter
 */
public class ForwardPathFilter implements GlobalFilter, Ordered{
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		//获取当前请求上下文的路由信息
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		//获取路由对应的URI
		URI routeUri = route.getUri();
		//获取URI中的统一资源标识符（Uniform Resource Identifier ）
		String scheme = routeUri.getScheme();
		//当scheme!=forward 或者已经路由时返回
		if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
			return chain.filter(exchange);
		}
		//重新构建exchange（请求重新转发到routeUri.getPath())
		exchange = exchange.mutate().request(
				exchange.getRequest().mutate().path(routeUri.getPath()).build())
				.build();
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return 0;
	}
}