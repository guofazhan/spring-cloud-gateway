package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * 创建一个谓词，该谓词指示请求是否用于云路由服务。
 * Creates a predicate which indicates if the request is intended for a Cloud Foundry Route Service.
 * @see <a href="https://docs.cloudfoundry.org/services/route-services.html">Cloud Foundry Route Service documentation</a>.
 * @author Andrew Fitzgerald
 */
public class CloudFoundryRouteServiceRoutePredicateFactory extends
		AbstractRoutePredicateFactory<Object> {

	public static final String X_CF_FORWARDED_URL = "X-CF-Forwarded-Url";
	public static final String X_CF_PROXY_SIGNATURE = "X-CF-Proxy-Signature";
	public static final String X_CF_PROXY_METADATA = "X-CF-Proxy-Metadata";
	/**
	 * 请求header校验谓语创建工厂
	 */
	private final HeaderRoutePredicateFactory factory = new HeaderRoutePredicateFactory();

	public CloudFoundryRouteServiceRoutePredicateFactory() {
		super(Object.class);
	}

	@Override
	public Predicate<ServerWebExchange> apply(
			Object unused) {
		//多个header校验已短路逻辑AND连接
		//X-CF-Proxy-Signature
		//X-CF-Proxy-Metadata
		//X-CF-Forwarded-Url
		return headerPredicate(X_CF_FORWARDED_URL)
				.and(headerPredicate(X_CF_PROXY_SIGNATURE))
				.and(headerPredicate(X_CF_PROXY_METADATA));
	}

	private Predicate<ServerWebExchange> headerPredicate(String header) {
		//创建配置
		HeaderRoutePredicateFactory.Config config = factory.newConfig();
		//设置校验的header名称
		config.setHeader(header);
		//设置校验的header值的匹配表达式
		config.setRegexp(".*");
		//返回谓语
		return factory.apply(config);
	}
}
