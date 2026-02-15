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
package ortus.boxlang.compiler.asmboxpiler.transformer.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import ortus.boxlang.compiler.asmboxpiler.MethodContextTracker;
import ortus.boxlang.compiler.asmboxpiler.Transpiler;
import ortus.boxlang.compiler.asmboxpiler.transformer.AbstractTransformer;
import ortus.boxlang.compiler.asmboxpiler.transformer.ReturnValueContext;
import ortus.boxlang.compiler.asmboxpiler.transformer.TransformerContext;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.expression.BoxPipeExpr;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IBoxContext.PipePlaceholder;

public class BoxPipeExprTransformer extends AbstractTransformer {

	public BoxPipeExprTransformer( Transpiler transpiler ) {
		super( transpiler );
	}

	@Override
	public List<AbstractInsnNode> transform( BoxNode _node_, TransformerContext context, ReturnValueContext returnContext ) throws IllegalStateException {
		final var				node	= ( BoxPipeExpr ) _node_;
		MethodContextTracker	tracker	= transpiler.getCurrentMethodContextTracker().get();

		var						nodes	= new ArrayList<AbstractInsnNode>();
		var						head	= node.getHead();
		var						rest	= node.getRest();

		// `rest` is guaranteed to have at least 1 element, by grammatical construction
		if ( rest.isEmpty() ) {
			throw new RuntimeException( "Unreachable: pipe expression empty `rest`" );
		}

		LabelNode			pipeExprEvalStart	= new LabelNode();
		LabelNode			pipeExprEvalEnd		= new LabelNode();
		LabelNode			pipeExprEvalCatch	= new LabelNode();
		LabelNode			exitOK				= new LabelNode();
		TryCatchBlockNode	catchHandler		= new TryCatchBlockNode( pipeExprEvalStart, pipeExprEvalEnd, pipeExprEvalCatch, null );
		tracker.addTryCatchBlock( catchHandler );

		nodes.addAll( tracker.loadCurrentContext() );
		// stack: [ctx]
		nodes.add( new InsnNode( Opcodes.DUP ) );
		// stack: [ctx, ctx]
		nodes.add( new InsnNode( Opcodes.DUP ) );
		// stack: [ctx, ctx, ctx]

		nodes.add( getCurrentPipePlaceholder() );
		// stack: [ctx, ctx, savedPlaceholder]

		nodes.add( new InsnNode( Opcodes.DUP ) );
		// stack: [ctx, ctx, savedPlaceholder, savedPlaceholder]

		// Need to also save the savedPlaceholder in a local. We have 2 cases:
		// - default/non-exceptional case: restore savedPlaceholder from stack value (should be a little faster than local, yeah?)
		// - exceptional case: restore savedPlaceholder from local
		// TODO: A single context only needs one "local saved placeholder" slot, so maybe we can ask the tracker for the current one, rather than
		// create a new one for every distinct pipe expr (e.g. `a = e1 |> e2; b = e3 |> e4` is 2 distinct exprs but only ever needs one local slot
		// to store the "current saved placeholder"). Hm, consider `(e1 |> (e2 |> e3) |> e4)`.
		var localSavedPlaceholder = tracker.storeNewVariable( Opcodes.ASTORE );
		nodes.addAll( localSavedPlaceholder.nodes() );
		// stack: [ctx, ctx, savedPlaceholder]

		{
			nodes.add( pipeExprEvalStart );

			// evalute head
			{
				// stack: [ctx, ctx, savedPlaceholder]

				nodes.add( new InsnNode( Opcodes.SWAP ) );
				// stack: [ctx, savedPlaceholder, ctx]

				// evaluate e1 in `e1 |> e2`
				nodes.addAll(
				    constructAndInitPipePlaceholderByEvaluatingPipeExpr( () -> {
					    return transpiler.transform( head, TransformerContext.NONE, ReturnValueContext.VALUE_OR_NULL );
				    } )
				);
				// stack: [ctx, savedPlaceholder, ctx, freshPlaceholder]

				nodes.add( setCurrentPipePlaceholder() );
				// stack: [ctx, savedPlaceholder]
			}

			// evaluate rest
			for ( int restIdx = 0; restIdx < rest.size(); restIdx++ ) {
				var	expr	= rest.get( restIdx );
				var	isLast	= restIdx == rest.size() - 1;

				if ( !isLast ) {
					// stack: [ctx, savedPlaceholder]

					nodes.add( new InsnNode( Opcodes.SWAP ) );
					// stack: [savedPlaceholder, ctx]

					nodes.add( new InsnNode( Opcodes.DUP_X1 ) );
					// stack: [ctx, savedPlaceholder, ctx]

					nodes.addAll(
					    constructAndInitPipePlaceholderByEvaluatingPipeExpr( () -> {
						    return transpiler.transform( expr, TransformerContext.NONE, ReturnValueContext.VALUE_OR_NULL );
					    } )
					);
					// stack: [ctx, savedPlaceholder, ctx, freshPlaceholder]

					nodes.add( setCurrentPipePlaceholder() );
					// stack: [ctx, savedPlaceholder]
				} else {
					// stack: [ctx, savedPlaceholder]

					nodes.addAll(
					    transpiler.transform( expr, TransformerContext.NONE, ReturnValueContext.VALUE_OR_NULL )
					);
					// stack: [ctx, savedPlaceholder, pipeResult]
				}
			}

			nodes.add( pipeExprEvalEnd );
		}

		{
			// success/non-exceptional path

			// stack: [ctx, savedPlaceholder, pipeResult]

			nodes.add( new InsnNode( Opcodes.DUP_X2 ) );
			// stack: [pipeResult, ctx, savedPlaceholder, pipeResult]

			nodes.add( new InsnNode( Opcodes.POP ) );
			// stack: [pipeResult, ctx, savedPlaceholder]

			nodes.add( setCurrentPipePlaceholder() );
			// stack: [pipeResult]

			if ( returnContext == ReturnValueContext.EMPTY ) { // do we need to also consider EMPTY_UNLESS_JUMPING
				nodes.add( new InsnNode( Opcodes.POP ) );
			}

			nodes.add( new JumpInsnNode( Opcodes.GOTO, exitOK ) );
		}

		{
			// exceptional path

			nodes.add( pipeExprEvalCatch );

			// stack: [exception]

			nodes.addAll( tracker.loadCurrentContext() );
			// stack: [exception, ctx]
			nodes.add( new VarInsnNode( Opcodes.ALOAD, localSavedPlaceholder.index() ) );
			// stack: [exception, ctx, savedPlaceholder]
			nodes.add( setCurrentPipePlaceholder() );
			// stack: [exception]
			nodes.add( new InsnNode( Opcodes.ATHROW ) );
			// exception rethrown
		}

		nodes.add( exitOK );

		return nodes;
	}

	// stack before: [ctx]
	// stack after: [PipePlaceholder]
	static private AbstractInsnNode getCurrentPipePlaceholder() {
		return new MethodInsnNode( Opcodes.INVOKEINTERFACE,
		    Type.getInternalName( IBoxContext.class ),
		    "getCurrentPipePlaceholder",
		    Type.getMethodDescriptor(
		        Type.getType( PipePlaceholder.class )
		    ),
		    true
		);
	}

	// stack before: [ctx, pipePlaceholder]
	// stack after: []
	static private AbstractInsnNode setCurrentPipePlaceholder() {
		return new MethodInsnNode( Opcodes.INVOKEINTERFACE,
		    Type.getInternalName( IBoxContext.class ),
		    "setCurrentPipePlaceholder",
		    Type.getMethodDescriptor(
		        Type.VOID_TYPE,
		        Type.getType( PipePlaceholder.class )
		    ),
		    true
		);
	}

	// stack before: []
	// stack after: [pipePlaceholder]
	static private List<AbstractInsnNode> constructAndInitPipePlaceholderByEvaluatingPipeExpr( Supplier<List<AbstractInsnNode>> pipeExpr ) {
		var nodes = new ArrayList<AbstractInsnNode>();

		nodes.add( new TypeInsnNode( Opcodes.NEW, Type.getType( PipePlaceholder.class ).getInternalName() ) );
		// stack: [freshPlaceholder]

		nodes.add( new InsnNode( Opcodes.DUP ) );
		// stack: [freshPlaceholder, freshPlaceholder]

		nodes.addAll( pipeExpr.get() );
		// stack: [freshPlaceholder, freshPlaceholder, obj]

		nodes.add(
		    new MethodInsnNode( Opcodes.INVOKESPECIAL, Type.getType( PipePlaceholder.class ).getInternalName(), "<init>", "(Ljava/lang/Object;)V", false ) );
		// stack: [freshPlaceholder]

		return nodes;
	}
}
