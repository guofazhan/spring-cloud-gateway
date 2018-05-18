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
 * 请求时间（Before）校验谓语创建工厂
 * @author Spencer Gibb
 */
public class BeforeRoutePredicateFactory extends AbstractRoutePredicateFactory<BeforeRoutePredicateFactory.Config> {

	public static final String DATETIME_KEY = "datetime";

	public BeforeRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList(DATETIME_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		//获取配置中的时间戳
		ZonedDateTime datetime = getZonedDateTime(config.getDatetime());
		return exchange -> {
			//获取请求当前的时间戳
			final ZonedDateTime now = ZonedDateTime.now();
			//对比当前时间与配置时间
			return now.isBefore(datetime);
		};
	}

	/**
	 * 时间(Before)谓语创建配置
	 */
	public static class Config {
		private String datetime;

		public String getDatetime() {
			return datetime;
		}

		public void setDatetime(String datetime) {
			this.datetime = datetime;
		}
	}
}
