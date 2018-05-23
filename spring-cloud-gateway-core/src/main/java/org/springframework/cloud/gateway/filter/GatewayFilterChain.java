package org.springframework.cloud.gateway.filter;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * 网关过滤链表接口
 * 用于过滤器的链式调用
 * Contract to allow a {@link WebFilter} to delegate to the next in the chain.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface GatewayFilterChain {

	/**
	 *  调用
	 * Delegate to the next {@code WebFilter} in the chain.
	 * @param exchange the current server exchange
	 * @return {@code Mono<Void>} to indicate when request handling is complete
	 */
	Mono<Void> filter(ServerWebExchange exchange);

}
