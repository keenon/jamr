// Generated from /home/keenon/Desktop/principled/src/main/antlr4/edu/stanford/nlp/stamr/AMR.g4 by ANTLR 4.4.1-dev
package nlp.stamr;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link AMRParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface AMRVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link AMRParser#unparenthesized_title}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnparenthesized_title(@NotNull AMRParser.Unparenthesized_titleContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#reference}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReference(@NotNull AMRParser.ReferenceContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#node}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNode(@NotNull AMRParser.NodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#throwaway}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitThrowaway(@NotNull AMRParser.ThrowawayContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#quote}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQuote(@NotNull AMRParser.QuoteContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#arg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArg(@NotNull AMRParser.ArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#terminal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerminal(@NotNull AMRParser.TerminalContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#parenthesized_title}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParenthesized_title(@NotNull AMRParser.Parenthesized_titleContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#alignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlignment(@NotNull AMRParser.AlignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(@NotNull AMRParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link AMRParser#introduction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntroduction(@NotNull AMRParser.IntroductionContext ctx);
}