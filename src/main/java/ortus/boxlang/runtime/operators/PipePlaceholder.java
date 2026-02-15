/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.runtime.operators;

import ortus.boxlang.runtime.context.IBoxContext;

/**
 * Evalutes the "unwrapped current pipePlaceholder", or throws if the current evaluation is not within a pipeExpr.
 * TODO: Syntactically it should not be possible to enter the "user wrote a pipe placeholder in a non-pipeExpr production" state.
 */
public class PipePlaceholder implements IOperator {

	/**
	 * @return The current (unwrapped) pipePlaceholder value, which can be null.
	 *         If there is no current pipePlaceholder, invoking this method will result in a RuntimeException.
	 */
	public static Object invoke( IBoxContext context ) {
		if ( context.getCurrentPipePlaceholder() instanceof ortus.boxlang.runtime.context.IBoxContext.PipePlaceholder( var value ) ) {
			return value;
		} else {
			throw new RuntimeException( "No current pipe placeholder available." );
		}
	}
}
