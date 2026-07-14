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
package ortus.boxlang.compiler;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ortus.boxlang.compiler.parser.BoxParser;
import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.exceptions.ExpressionException;

public class PipeExprTest {

	static BoxRuntime	instance;
	IBoxContext			context;
	IScope				variables;
	static Key			result	= new Key( "result" );

	@BeforeAll
	public static void setUp() {
		instance = BoxRuntime.getInstance( true );
	}

	@AfterAll
	public static void teardown() {

	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext( instance.getRuntimeContext() );
		variables	= context.getScopeNearby( VariablesScope.name );
	}

	@Test
	public void ez1() {
		instance.executeSource(
		    """
		    	result = "foo" |> @;
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( "foo" );
	}

	@Test
	public void ez2() {
		instance.executeSource(
		    """
		    	result = "foo"
		    		|> @ & "bar";
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( "foobar" );
	}

	@Test
	public void ez3() {
		instance.executeSource(
		    """
		    	result = result2 = 50
		    		|> @ - 10
		    		|> 2 + @ + 0
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( 42 );
		assertThat( variables.get( Key.of( "result2" ) ) ).isEqualTo( 42 );
	}

	@Test
	public void ez4() {
		instance.executeSource(
		    """
		       m = 0
		       result = 42
		    		|> (() => { m = 1; })()
		    		|> 44;
		    """,
		    context, BoxSourceType.BOXSCRIPT );

		assertThat( variables.get( result ) ).isEqualTo( 44 );
		assertThat( variables.get( Key.of( "m" ) ) ).isEqualTo( 1 );
	}

	@Test
	public void ez5() {
		instance.executeSource(
		    """
		    function foo(required array v) { return v[2] }
		       result = ["x","y","z"] |> foo(v = @);
		    """,
		    context, BoxSourceType.BOXSCRIPT );

		assertThat( variables.get( result ) ).isEqualTo( "y" );
	}

	@Test
	public void capturedPlaceholder() {
		instance.executeSource(
		    """
		    function foo(f) {
		    	return () => f() & " (plus a little bit more)";
		    }

		      	f = 0;
		      	g = 0;
		      	result1 = "x"
		      		|> (() => {
		      			f = () => @ // @ captures outer current pipeline value "x"
		      			g = () => [@, f] // @ captures outer current pipeline value "x"
		      				|> {a: @[1], f: @[2]} // @ now means "current pipeline value"
		      				|> "#@.a##@.f()#" // @ now means "current pipeline value"
		      		})()
		      		|> (() => 42)()
		      		|> "z";

		      	result2 = f();

		      	result3 = g();

		    result4 = "ok" |> foo(() => @) |> @()
		    result5_f = "ok2" |> foo(() => @)
		    result5 = result5_f();
		      """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( Key.of( "result1" ) ) ).isEqualTo( "z" );
		assertThat( variables.get( Key.of( "result2" ) ) ).isEqualTo( "x" );
		assertThat( variables.get( Key.of( "result3" ) ) ).isEqualTo( "xx" );
		assertThat( variables.get( Key.of( "result4" ) ) ).isEqualTo( "ok (plus a little bit more)" );
		assertThat( variables.get( Key.of( "result5" ) ) ).isEqualTo( "ok2 (plus a little bit more)" );
	}

	@Test
	public void indexedAccess() {
		instance.executeSource(
		    """
		    result = (() => { return {z: [99]}})
		      		|> @()
		      		|> @.z
		    	|> @[1]
		      """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( Key.of( "result" ) ) ).isEqualTo( 99 );
	}

	@Test
	public void x1() {
		instance.executeSource(
		    """
		    	parensIfNonEmpty = (required string s) => s == "" ? "" : "(#s#)"
		    	leadingSpaceIfNonEmpty = (required string s) => s == "" ? "" : " #s#";

		    	name = "foo"
		    	extra = "bar"

		    	result = name & (extra |> parensIfNonEmpty(@) |> leadingSpaceIfNonEmpty(@));

		    	doubleIt = (required string v) => v & v;
		    	quadrupleIt = (required string v) => v
		    		|> doubleIt(@)
		    		|> doubleIt(@)

		    	result2 = quadrupleIt("asdf")
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( "foo (bar)" );
		assertThat( variables.get( Key.of( "result2" ) ) ).isEqualTo( "asdfasdfasdfasdf" );
	}

	@Test
	public void nestedPipes() {
		instance.executeSource(
		    """
		     	result = "foo"
		    |> (
		    	{x:@} |> "#@.x##@.x#"
		    ) |> @ & @
		     """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( "foofoofoofoo" );
	}

	@Test
	public void grammarSanityCheck() {
		instance.executeSource(
		    """
		    a = 5 |> @ % @; // 5 mod 5
		    b = 5 |> @ % 4; // 5 mod 4
		    c = 5 |> 4 % @; // 4 mod 5

		    d = 2 |> (() => {
		    	try {
		    		throw "oops";
		    	}
		    	catch (any e) {
		    		return @ + 1;
		    	}
		    })()
		       """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		var	a	= ( ( BigDecimal ) variables.get( "a" ) ).intValue();
		var	b	= ( ( BigDecimal ) variables.get( "b" ) ).intValue();
		var	c	= ( ( BigDecimal ) variables.get( "c" ) ).intValue();

		assertThat( a ).isEqualTo( 5 % 5 );
		assertThat( a ).isEqualTo( 0 );

		assertThat( b ).isEqualTo( 5 % 4 );
		assertThat( b ).isEqualTo( 1 );

		assertThat( c ).isEqualTo( 4 % 5 );
		assertThat( c ).isEqualTo( 4 );

		assertThat( variables.get( "d" ) ).isEqualTo( 3 );
	}

	@Test
	public void catchFinallyCtx() {
		instance.executeSource(
		    """
		    try {
		    	throw "oops";
		    }
		    catch (any e) {
		    	result_catch = 0 |> @ + 1;
		    }

		       	try {
		    	0+0;
		    }
		    finally {
		    	result_finally = 2 |> @ + 1;
		    }
		       """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( "result_catch" ) ).isEqualTo( 1 );
		assertThat( variables.get( "result_finally" ) ).isEqualTo( 3 );
	}

	@Test
	public void evalReturnContextModes() {
		instance.executeSource(
		    """
		    	(() => {
		    		try {
		    	// Evaluation here is "cannot leave things on stack"
		    // Success is measured by generating valid bytecode.
		    // Failing to properly pop the pipe expr result off the stack here
		    // will result in a bytecode verification error.
		    			1 |> 2;
		    		}
		    		catch (any e) {

		    		}
		    	})();

		    	result = (() => {
		    		try {
		    			return 1 |> 2; // evaluation here is "leave things on stack"
		    		}
		    		catch (any e) {

		    		}
		    	})();
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( 2 );
	}

	@Test
	public void exceptionsInPipeChainResetCurrentPipePlaceholder() {
		instance.executeSource(
		    """
		    	result = 0;

		    	"a"
		    	|> (() => {
		    		try {
		    			"b"
		    				|> "c"
		    				|> throw("oops") // placeholder is "c" here, but throwing should reset it to "a"
		    				|> "d"
		    		}
		    		catch (any e) {
		    			result = @;
		    		}
		    	})();
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( result ) ).isEqualTo( "a" );
	}

	@Test
	public void assignmentToPlaceholderIllegal() {
		try {
			// TODO: could probably catch this as a semantic error, right now it is a runtime error.
			instance.executeSource(
			    """
			    	"a" |> (() => {
			    		@ = 42;
			    	})()
			    """,
			    context,
			    BoxSourceType.BOXSCRIPT
			);

			throw new RuntimeException( "assumed unreachable" );
		} catch ( ExpressionException e ) {
			// TODO: the thing throwing the exception dumps a bunch of stuff to stdout that would be nice to silence in the case of this test
			assertThat( e.getMessage() ).startsWith( "You cannot assign a value to BoxPipePlaceholder" );
		}
	}

	@Test
	public void placeholderUtteranceInNonPipeExprIsIllegal() throws Throwable {
		{
			BoxParser		parser	= new BoxParser();
			ParsingResult	result	= parser.parse( "1 + @" );
			assertThat( result.isCorrect() ).isFalse();
			assertThat( result.getIssues().size() ).isEqualTo( 1 );
			assertThat( result.getIssues().get( 0 ).getMessage() ).startsWith( "A pipe placeholder cannot occur in a non-pipe context." );
		}

		{
			BoxParser		parser	= new BoxParser();
			ParsingResult	result	= parser.parse( "1 |> 1 + @" );
			assertThat( result.isCorrect() ).isTrue();
		}
	}

	@Test
	public void captures() {
		instance.executeSource(
		    """
		    	f = null;

		    	42
					|> (() => {
						f = () => @
					})()
					|> 43
					|> 44
					|> 45;

				a = f()
				b = f()
		    """,
		    context,
		    BoxSourceType.BOXSCRIPT
		);

		assertThat( variables.get( "a" ) ).isEqualTo( 42 );
		assertThat( variables.get( "b" ) ).isEqualTo( 42 );
	}
}
