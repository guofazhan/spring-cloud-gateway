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

package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory.getZonedDateTime;

/**
 * 请求时间（后）校验谓语创建工厂
 * @author Spencer Gibb
 */
public class AfterRoutePredicateFactory extends AbstractRoutePredicateFactory<AfterRoutePredicateFactory.Config> {

	public static final String DATETIME_KEY = "datetime";

	public AfterRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList(DATETIME_KEY);
	}

	/**
	 * 创建时间（后）谓语
	 * @param config
	 * @return
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		//获取配置的时间戳
		ZonedDateTime datetime = getZonedDateTime(config.getDatetime());
		return exchange -> {
			//获取请求当前的时间戳
			final ZonedDateTime now = ZonedDateTime.now();
			//校验请求时间是否在配置时间后//返回校验结果
			return now.isAfter(datetime);
		};
	}

	/**
	 * 时间（后）谓语创建配置
	 */
	public static class Config {
		/**
		 * 时间
		 */
		private String datetime;

		public String getDatetime() {
			return datetime;
		}

		public void setDatetime(String datetime) {
			this.datetime = datetime;
		}
	}

}
