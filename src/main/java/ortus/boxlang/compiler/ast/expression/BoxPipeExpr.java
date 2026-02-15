/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.compiler.ast.expression;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ortus.boxlang.compiler.ast.BoxExpression;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Position;
import ortus.boxlang.compiler.ast.visitor.ReplacingBoxVisitor;
import ortus.boxlang.compiler.ast.visitor.VoidBoxVisitor;

/**
 * AST Node representing a pipe expr `e1 |> e2 |> ...`
 */
public class BoxPipeExpr extends BoxExpression {

	private BoxExpression		head;
	private List<BoxExpression>	rest;

	/**
	 * Creates the AST node
	 *
	 * @param head       Single expr `e1` in `e1 |> e2 |> ... |> e_n`
	 * @param rest       List of exprs `e2` through `e_n` in `e1 |> e2 |> ... |> e_n`. Must be at least of length 1 - this length invariant should be enforced grammatically.
	 * @param position   position of the statement in the source code
	 * @param sourceText source code that originated the Node
	 *
	 * @see BoxBinaryOperator for the supported operators
	 */
	public BoxPipeExpr( BoxExpression head, List<BoxExpression> rest, Position position, String sourceText ) {
		super( position, sourceText );
		setHead( head );
		setRest( rest );
	}

	public BoxExpression getHead() {
		return head;
	}

	public List<BoxExpression> getRest() {
		return rest;
	}

	public void setHead( BoxExpression head ) {
		replaceChildren( this.head, head );
		this.head = head;
		this.head.setParent( this );
	}

	public void setRest( List<BoxExpression> rest ) {
		if ( rest.isEmpty() ) {
			throw new RuntimeException( "Unreachable: empty BoxPipeExpr `rest`" );
		}

		replaceChildren( this.rest, rest );
		this.rest = rest;
		this.rest.forEach( node -> node.setParent( this ) );
	}

	@Override
	public Map<String, Object> toMap() {
		Map<String, Object>	map		= super.toMap();

		Map<String, Object>	headMap	= head.toMap();
		Map<String, Object>	restMap	= IntStream
		    .range( 0, this.rest.size() )
		    .boxed()
		    .collect( Collectors.toMap(
		        i -> Integer.toString( i ),
		        i -> rest.get( i ).toMap()
		    ) );

		map.put( "head", headMap );
		map.put( "rest", restMap );

		return map;
	}

	public void accept( VoidBoxVisitor v ) {
		v.visit( this );
	}

	public BoxNode accept( ReplacingBoxVisitor v ) {
		return v.visit( this );
	}

}
