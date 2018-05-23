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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * 请求权重校验谓语创建工厂
 * @author Spencer Gibb
 */
//TODO: make this a generic Choose out of group predicate?
public class WeightRoutePredicateFactory extends AbstractRoutePredicateFactory<WeightConfig> implements ApplicationEventPublisherAware {

	private static final Log log = LogFactory.getLog(WeightRoutePredicateFactory.class);

	public static final String GROUP_KEY = WeightConfig.CONFIG_PREFIX + ".group";
	public static final String WEIGHT_KEY = WeightConfig.CONFIG_PREFIX + ".weight";

	private ApplicationEventPublisher publisher;

	public WeightRoutePredicateFactory() {
		super(WeightConfig.class);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(GROUP_KEY, WEIGHT_KEY);
	}

	@Override
	public String shortcutFieldPrefix() {
			return WeightConfig.CONFIG_PREFIX;
	}

	@Override
	public void beforeApply(WeightConfig config) {
		if (publisher != null) {
			publisher.publishEvent(new WeightDefinedEvent(this, config));
		}
	}

	@Override
	public Predicate<ServerWebExchange> apply(WeightConfig config) {
		return exchange -> {
			//获取当前请求权重信息
			Map<String, String> weights = exchange.getAttributeOrDefault(WEIGHT_ATTR,
					Collections.emptyMap());

			//获取当前请求路由ID信息
			String routeId = exchange.getAttribute(GATEWAY_PREDICATE_ROUTE_ATTR);

			// all calculations and comparison against random num happened in
			// WeightCalculatorWebFilter
			//获取配置组信息
			String group = config.getGroup();
			//校验权重信息中是否包含配置的组信息
			if (weights.containsKey(group)) {

				//根据组信息在权重集合获取路由ID
				String chosenRoute = weights.get(group);
				if (log.isTraceEnabled()) {
					log.trace("in group weight: "+ group + ", current route: " + routeId +", chosen route: " + chosenRoute);
				}
				//校验路由ID是否匹配
				return routeId.equals(chosenRoute);
			}

			return false;
		};
	}
}