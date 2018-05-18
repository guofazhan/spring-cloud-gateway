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

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.NotEmpty;

import org.springframework.http.HttpCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * cookie校验谓语创建工厂
 * @author Spencer Gibb
 */
public class CookieRoutePredicateFactory extends AbstractRoutePredicateFactory<CookieRoutePredicateFactory.Config> {

	public static final String NAME_KEY = "name";
	public static final String REGEXP_KEY = "regexp";

	public CookieRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, REGEXP_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return exchange -> {
			//获取请求中给定名称的cookie信息
			List<HttpCookie> cookies = exchange.getRequest().getCookies().get(config.name);
			//校验cookie的值是否匹配正则表达式
			if (cookies == null) {
				return false;
			}
			for (HttpCookie cookie : cookies) {
				if (cookie.getValue().matches(config.regexp)) {
					return true;
				}
			}
			return false;
		};
	}

	/**
	 * cookie校验谓语创建配置
	 */
	@Validated
	public static class Config {

		/**
		 * cookie名称
		 */
		@NotEmpty
		private String name;
		/**
		 * 配置正则
		 */
		@NotEmpty
		private String regexp;

		public String getName() {
			return name;
		}

		public Config setName(String name) {
			this.name = name;
			return this;
		}

		public String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}
	}
}
