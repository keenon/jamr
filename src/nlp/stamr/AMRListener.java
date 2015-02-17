// Generated from /home/keenon/Desktop/principled/src/main/antlr4/edu/stanford/nlp/stamr/AMR.g4 by ANTLR 4.4.1-dev
package nlp.stamr;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link AMRParser}.
 */
public interface AMRListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link AMRParser#unparenthesized_title}.
	 * @param ctx the parse tree
	 */
	void enterUnparenthesized_title(@NotNull AMRParser.Unparenthesized_titleContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#unparenthesized_title}.
	 * @param ctx the parse tree
	 */
	void exitUnparenthesized_title(@NotNull AMRParser.Unparenthesized_titleContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#reference}.
	 * @param ctx the parse tree
	 */
	void enterReference(@NotNull AMRParser.ReferenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#reference}.
	 * @param ctx the parse tree
	 */
	void exitReference(@NotNull AMRParser.ReferenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#node}.
	 * @param ctx the parse tree
	 */
	void enterNode(@NotNull AMRParser.NodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#node}.
	 * @param ctx the parse tree
	 */
	void exitNode(@NotNull AMRParser.NodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#throwaway}.
	 * @param ctx the parse tree
	 */
	void enterThrowaway(@NotNull AMRParser.ThrowawayContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#throwaway}.
	 * @param ctx the parse tree
	 */
	void exitThrowaway(@NotNull AMRParser.ThrowawayContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#quote}.
	 * @param ctx the parse tree
	 */
	void enterQuote(@NotNull AMRParser.QuoteContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#quote}.
	 * @param ctx the parse tree
	 */
	void exitQuote(@NotNull AMRParser.QuoteContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#arg}.
	 * @param ctx the parse tree
	 */
	void enterArg(@NotNull AMRParser.ArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#arg}.
	 * @param ctx the parse tree
	 */
	void exitArg(@NotNull AMRParser.ArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#terminal}.
	 * @param ctx the parse tree
	 */
	void enterTerminal(@NotNull AMRParser.TerminalContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#terminal}.
	 * @param ctx the parse tree
	 */
	void exitTerminal(@NotNull AMRParser.TerminalContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#parenthesized_title}.
	 * @param ctx the parse tree
	 */
	void enterParenthesized_title(@NotNull AMRParser.Parenthesized_titleContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#parenthesized_title}.
	 * @param ctx the parse tree
	 */
	void exitParenthesized_title(@NotNull AMRParser.Parenthesized_titleContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#alignment}.
	 * @param ctx the parse tree
	 */
	void enterAlignment(@NotNull AMRParser.AlignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#alignment}.
	 * @param ctx the parse tree
	 */
	void exitAlignment(@NotNull AMRParser.AlignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(@NotNull AMRParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(@NotNull AMRParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link AMRParser#introduction}.
	 * @param ctx the parse tree
	 */
	void enterIntroduction(@NotNull AMRParser.IntroductionContext ctx);
	/**
	 * Exit a parse tree produced by {@link AMRParser#introduction}.
	 * @param ctx the parse tree
	 */
	void exitIntroduction(@NotNull AMRParser.IntroductionContext ctx);
}