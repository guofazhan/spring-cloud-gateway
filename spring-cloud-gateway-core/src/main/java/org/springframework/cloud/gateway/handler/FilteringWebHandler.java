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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import reactor.core.publisher.Mono;

/**
 * 通过过滤器处理web请求的处理器
 * WebHandler that delegates to a chain of {@link GlobalFilter} instances and
 * {@link GatewayFilterFactory} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 */
public class FilteringWebHandler implements WebHandler {
	protected static final Log logger = LogFactory.getLog(FilteringWebHandler.class);

	/**
	 * 全局过滤器
	 */
	private final List<GatewayFilter> globalFilters;

	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this.globalFilters = loadFilters(globalFilters);
	}

	/**
	 * 包装加载全局的过滤器，将全局过滤器包装成GatewayFilter
	 * @param filters
	 * @return
	 */
	private static List<GatewayFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream()
				.map(filter -> {
					//将所有的全局过滤器包装成网关过滤器
					GatewayFilterAdapter gatewayFilter = new GatewayFilterAdapter(filter);
					//判断全局过滤器是否实现了可排序接口
					if (filter instanceof Ordered) {
						int order = ((Ordered) filter).getOrder();
						//包装成可排序的网关过滤器
						return new OrderedGatewayFilter(gatewayFilter, order);
					}
					return gatewayFilter;
				}).collect(Collectors.toList());
	}

    /* TODO: relocate @EventListener(RefreshRoutesEvent.class)
    void handleRefresh() {
        this.combinedFiltersForRoute.clear();
    }*/

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		//获取请求上下文设置的路由实例
		Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
		//获取路由定义下的网关过滤器集合
		List<GatewayFilter> gatewayFilters = route.getFilters();

		//组合全局的过滤器与路由配置的过滤器
		List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
		//添加路由配置过滤器到集合尾部
		combined.addAll(gatewayFilters);
		//对过滤器进行排序
		//TODO: needed or cached?
		AnnotationAwareOrderComparator.sort(combined);

		logger.debug("Sorted gatewayFilterFactories: "+ combined);
		//创建过滤器链表对其进行链式调用
		return new DefaultGatewayFilterChain(combined).filter(exchange);
	}

	/**
	 * 网关过滤的链表，用于过滤器的链式调用
	 */
	private static class DefaultGatewayFilterChain implements GatewayFilterChain {

		private final int index;
		private final List<GatewayFilter> filters;

		public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
			this.filters = filters;
			this.index = 0;
		}

		private DefaultGatewayFilterChain(DefaultGatewayFilterChain parent, int index) {
			this.filters = parent.getFilters();
			this.index = index;
		}

		public List<GatewayFilter> getFilters() {
			return filters;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			return Mono.defer(() -> {
				if (this.index < filters.size()) {
					GatewayFilter filter = filters.get(this.index);
					DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);
					return filter.filter(exchange, chain);
				} else {
					return Mono.empty(); // complete
				}
			});
		}
	}

	/**
	 * 全局过滤器的包装类，将全局路由包装成统一的网关过滤器
	 */
	private static class GatewayFilterAdapter implements GatewayFilter {

		private final GlobalFilter delegate;

		public GatewayFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("GatewayFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}
	}

}
