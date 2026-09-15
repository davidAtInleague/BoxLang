package ortus.boxlang.runtime.bifs.global.async;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

public class ThreadJoinTest {

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

	@DisplayName( "It can join all threads" )
	@Test
	public void testCanJOinAllThreads() {
		// @formatter:off
		instance.executeSource(
		    """
				thread name="myThread" {
					sleep( 2000 )
				}
				thread name="myThread2" {
					sleep( 2000 )
				}
		    	threadJoin()
				result = myThread;
				result2 = myThread2;
		    """,
		    context, BoxSourceType.CFSCRIPT );
		// @formatter:on
		assertThat( variables.getAsStruct( result ).get( Key.status ) ).isEqualTo( "COMPLETED" );
		assertThat( variables.getAsStruct( Key.of( "result2" ) ).get( Key.status ) ).isEqualTo( "COMPLETED" );
	}

	@DisplayName( "It can join thread no timeout" )
	@Test
	public void testCanJoinThreadNoTimeout() {
		// @formatter:off
		instance.executeSource(
		    """
		       thread name="myThread" {
		    	   sleep( 2000 )
		       }
		       	threadJoin( "myThread" )
				result = myThread;
		    """,
		    context, BoxSourceType.CFSCRIPT );
		// @formatter:on
		assertThat( variables.getAsStruct( result ).get( Key.status ) ).isEqualTo( "COMPLETED" );
	}

	@DisplayName( "It can join thread zero timeout" )
	@Test
	public void testCanJoinThreadZeroTimeout() {
		// @formatter:off
		instance.executeSource(
		    """
		       	thread name="myThread" {
		    		sleep( 2000 )
		       	}
				threadJoin( "myThread", 0 )
		       	result = myThread;
		    """,
		    context, BoxSourceType.CFSCRIPT );
		// @formatter:on
		assertThat( variables.getAsStruct( result ).get( Key.status ) ).isEqualTo( "COMPLETED" );
	}

	/**
	 * 
	 * sometimes this hangs, sometimes it passes
	 * 
	 */
	@DisplayName( "joining thousands of uniquely named virtual threads does not hang" )
	@Test
	@Timeout( value = 30, unit = TimeUnit.SECONDS )
	public void testJoinThousandsOfVirtualThreads() {
		// @formatter:off
		instance.executeSource(
		    """
				n = 5000;
				completed = createObject( "java", "java.util.concurrent.atomic.AtomicInteger" ).init( 0 );
				latch = createObject( "java", "java.util.concurrent.CountDownLatch" ).init( 1 );
				names = [];
				for ( i = 1; i <= n; i++ ) {
					names.append( "burst_#i#" );
					thread name="burst_#i#" virtual="true" {
						latch.await();
						completed.incrementAndGet();
					}
				}
				latch.countDown();
				thread action="join" name="#names.toList( "," )#";
				result = completed.get();
		    """,
		    context, BoxSourceType.CFSCRIPT );
		// @formatter:on
		assertThat( variables.get( result ) ).isEqualTo( 5000 );
	}

	/**
	 * 
	 * sometimes this hangs, sometimes it passes
	 * 
	 */
	@DisplayName( "" )
	@Test
	@Timeout( value = 30, unit = TimeUnit.SECONDS )
	public void testThousandsOfVirtualThreads() {
		// @formatter:off
		instance.executeSource(
		    """
				n = 5000;
				completed = createObject( "java", "java.util.concurrent.atomic.AtomicInteger" ).init( 0 );
				latch = createObject( "java", "java.util.concurrent.CountDownLatch" ).init( 1 );
				names = [];

				ids = []
				for (i = 1; i <= n; i++) {
					ids.append(i)
				}

				futures = ids.map((i) => {
					var future = createObject("java", "java.util.concurrent.CompletableFuture").init();
					var doIt = () => {
						future.complete(i);
					}
					names.append( "burst_#i#" );
					thread name="burst_#i#" virtual="true" doit=doit {
						latch.await();
						doit()
					}
					return future;
				})

				latch.countDown();
				result = futures.map((v) => v.get())
				expected = ids;
		    """,
		    context, BoxSourceType.CFSCRIPT );
		// @formatter:on

		assertThat( variables.get( result ) ).isEqualTo( variables.get( "expected" ) );
	}
}
