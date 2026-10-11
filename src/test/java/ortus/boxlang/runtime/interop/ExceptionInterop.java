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
package ortus.boxlang.runtime.interop;

import java.io.IOException;

/**
 * Throws a checked exception and an unchecked exception, and accepts only those types.
 * A catch variable that is still the BoxRuntimeException wrapper cannot select {@link #accepts(IOException)}.
 */
public class ExceptionInterop {

	public static void throwChecked() throws IOException {
		throw new IOException( "checked" );
	}

	public static void throwUnchecked() {
		throw new IllegalStateException( "unchecked" );
	}

	public static boolean accepts( IOException error ) {
		return "checked".equals( error.getMessage() );
	}

	public static boolean accepts( IllegalStateException error ) {
		return "unchecked".equals( error.getMessage() );
	}

}
