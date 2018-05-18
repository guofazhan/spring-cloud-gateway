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
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

import reactor.core.publisher.Mono;

/**
 * 负载均衡客户端过滤器
 * 作用，通过负载均衡客户端服务服务实例信息，通过服务实例信息构建路由的真实URI信息保存到请求的上下文中
 * @author Spencer Gibb
 * @author Tim Ysewyn
 */
public class LoadBalancerClientFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(LoadBalancerClientFilter.class);
	/**
	 * 排序字段
	 */
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10100;

	/**
	 * 负载均衡客户端
	 */
	private final LoadBalancerClient loadBalancer;

	public LoadBalancerClientFilter(LoadBalancerClient loadBalancer) {
		this.loadBalancer = loadBalancer;
	}

	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		//获取当前请求的路由的url属性
		URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		//获取当前请求路由的URI前缀信息
		String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
		//校验前缀信息是否为LB
		if (url == null || (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
			return chain.filter(exchange);
		}
		//添加原始的请求URL到请求的上下文中
		//preserve the original url
		addOriginalRequestUrl(exchange, url);

		log.trace("LoadBalancerClientFilter url before: " + url);

		//通过负载均衡客户端获取服务实例信息
		final ServiceInstance instance = loadBalancer.choose(url.getHost());

		if (instance == null) {
			throw new NotFoundException("Unable to find instance for " + url.getHost());
		}

		//获取当前请求的URI
		URI uri = exchange.getRequest().getURI();

		// if the `lb:<scheme>` mechanism was used, use `<scheme>` as the default,
		// if the loadbalancer doesn't provide one.
		String overrideScheme = null;
		if (schemePrefix != null) {
			overrideScheme = url.getScheme();
		}

		//构建真实的请求URI
		URI requestUrl = loadBalancer.reconstructURI(new DelegatingServiceInstance(instance, overrideScheme), uri);

		log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
		//设置真实的路由URI到上下问环境中
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
		return chain.filter(exchange);
	}

	/**
	 * 服务实例包装类
	 */
	class DelegatingServiceInstance implements ServiceInstance {
		/**
		 * 目标服务实例
		 */
		final ServiceInstance delegate;
		/**
		 * 重写的Scheme
		 */
		private String overrideScheme;

		DelegatingServiceInstance(ServiceInstance delegate, String overrideScheme) {
			this.delegate = delegate;
			this.overrideScheme = overrideScheme;
		}

		@Override
		public String getServiceId() {
			return delegate.getServiceId();
		}

		@Override
		public String getHost() {
			return delegate.getHost();
		}

		@Override
		public int getPort() {
			return delegate.getPort();
		}

		@Override
		public boolean isSecure() {
			return delegate.isSecure();
		}

		@Override
		public URI getUri() {
			return delegate.getUri();
		}

		@Override
		public Map<String, String> getMetadata() {
			return delegate.getMetadata();
		}

		@Override
		public String getScheme() {
			String scheme = delegate.getScheme();
			if (scheme != null) {
				return scheme;
			}
			return this.overrideScheme;
		}

	}
}
