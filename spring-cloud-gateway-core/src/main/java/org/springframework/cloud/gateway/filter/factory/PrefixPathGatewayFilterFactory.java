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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ALREADY_PREFIXED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * 路径前缀过滤器创建工厂
 * 用于在请求的path上添加前缀信息
 * @author Spencer Gibb
 */
public class PrefixPathGatewayFilterFactory extends AbstractGatewayFilterFactory<PrefixPathGatewayFilterFactory.Config> {

	private static final Log log = LogFactory.getLog(PrefixPathGatewayFilterFactory.class);

	public static final String PREFIX_KEY = "prefix";

	public PrefixPathGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PREFIX_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {

			boolean alreadyPrefixed = exchange.getAttributeOrDefault(GATEWAY_ALREADY_PREFIXED_ATTR, false);
			if (alreadyPrefixed) {
				return chain.filter(exchange);
			}
			//设置前缀添加状态属性
			exchange.getAttributes().put(GATEWAY_ALREADY_PREFIXED_ATTR, true);

			//获取当前请求ServerHttpRequest
			ServerHttpRequest req = exchange.getRequest();
			//保存原始URI到上下文环境中
			addOriginalRequestUrl(exchange, req.getURI());
			//组装添加前缀后的路径信息
			String newPath = config.prefix + req.getURI().getRawPath();

			//构建添加前缀路径的新ServerHttpRequest
			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			//设置新请求URI到上下文环境
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, request.getURI());

			if (log.isTraceEnabled()) {
				log.trace("Prefixed URI with: "+config.prefix+" -> "+request.getURI());
			}

			//将新请求信息添加到调度器中
			return chain.filter(exchange.mutate().request(request).build());
		};
	}

	public static class Config {
		private String prefix;

		public String getPrefix() {
			return prefix;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}
	}
}
