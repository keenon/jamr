// Generated from /home/keenon/Desktop/principled/src/main/antlr4/edu/stanford/nlp/stamr/AMR.g4 by ANTLR 4.4.1-dev
package nlp.stamr;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class AMRParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.4.1-dev", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__8=1, T__7=2, T__6=3, T__5=4, T__4=5, T__3=6, T__2=7, T__1=8, T__0=9, 
		QUOTE=10, LABEL=11, NUMBER=12, POLARITY=13, WHITESPACE=14;
	public static final String[] tokenNames = {
		"<INVALID>", "'/'", "'GUESS'", "'ACTUAL'", "'('", "')'", "':'", "'['", 
		"']'", "'='", "QUOTE", "LABEL", "NUMBER", "POLARITY", "WHITESPACE"
	};
	public static final int
		RULE_node = 0, RULE_arg = 1, RULE_parenthesized_title = 2, RULE_unparenthesized_title = 3, 
		RULE_alignment = 4, RULE_throwaway = 5, RULE_introduction = 6, RULE_reference = 7, 
		RULE_value = 8, RULE_terminal = 9, RULE_quote = 10;
	public static final String[] ruleNames = {
		"node", "arg", "parenthesized_title", "unparenthesized_title", "alignment", 
		"throwaway", "introduction", "reference", "value", "terminal", "quote"
	};

	@Override
	public String getGrammarFileName() { return "AMR.g4"; }

	@Override
	public String[] getTokenNames() { return tokenNames; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public AMRParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class NodeContext extends ParserRuleContext {
		public ArgContext arg(int i) {
			return getRuleContext(ArgContext.class,i);
		}
		public Parenthesized_titleContext parenthesized_title() {
			return getRuleContext(Parenthesized_titleContext.class,0);
		}
		public Unparenthesized_titleContext unparenthesized_title() {
			return getRuleContext(Unparenthesized_titleContext.class,0);
		}
		public AlignmentContext alignment() {
			return getRuleContext(AlignmentContext.class,0);
		}
		public List<ArgContext> arg() {
			return getRuleContexts(ArgContext.class);
		}
		public NodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_node; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitNode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NodeContext node() throws RecognitionException {
		NodeContext _localctx = new NodeContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_node);
		int _la;
		try {
			setState(39);
			switch (_input.LA(1)) {
			case T__5:
				enterOuterAlt(_localctx, 1);
				{
				setState(22); match(T__5);
				setState(23); parenthesized_title();
				setState(25);
				_la = _input.LA(1);
				if (_la==T__2) {
					{
					setState(24); alignment();
					}
				}

				setState(30);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__3) {
					{
					{
					setState(27); arg();
					}
					}
					setState(32);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(33); match(T__4);
				}
				break;
			case QUOTE:
			case LABEL:
			case NUMBER:
			case POLARITY:
				enterOuterAlt(_localctx, 2);
				{
				setState(35); unparenthesized_title();
				setState(37);
				_la = _input.LA(1);
				if (_la==T__2) {
					{
					setState(36); alignment();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgContext extends ParserRuleContext {
		public TerminalNode LABEL() { return getToken(AMRParser.LABEL, 0); }
		public NodeContext node() {
			return getRuleContext(NodeContext.class,0);
		}
		public ArgContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arg; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterArg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitArg(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitArg(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgContext arg() throws RecognitionException {
		ArgContext _localctx = new ArgContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_arg);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(41); match(T__3);
			setState(42); match(LABEL);
			setState(43); node();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Parenthesized_titleContext extends ParserRuleContext {
		public IntroductionContext introduction() {
			return getRuleContext(IntroductionContext.class,0);
		}
		public ReferenceContext reference() {
			return getRuleContext(ReferenceContext.class,0);
		}
		public Parenthesized_titleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parenthesized_title; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterParenthesized_title(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitParenthesized_title(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitParenthesized_title(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Parenthesized_titleContext parenthesized_title() throws RecognitionException {
		Parenthesized_titleContext _localctx = new Parenthesized_titleContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_parenthesized_title);
		try {
			setState(47);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(45); introduction();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(46); reference();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Unparenthesized_titleContext extends ParserRuleContext {
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public TerminalContext terminal() {
			return getRuleContext(TerminalContext.class,0);
		}
		public QuoteContext quote() {
			return getRuleContext(QuoteContext.class,0);
		}
		public Unparenthesized_titleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unparenthesized_title; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterUnparenthesized_title(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitUnparenthesized_title(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitUnparenthesized_title(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Unparenthesized_titleContext unparenthesized_title() throws RecognitionException {
		Unparenthesized_titleContext _localctx = new Unparenthesized_titleContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_unparenthesized_title);
		try {
			setState(52);
			switch (_input.LA(1)) {
			case LABEL:
				enterOuterAlt(_localctx, 1);
				{
				setState(49); terminal();
				}
				break;
			case NUMBER:
			case POLARITY:
				enterOuterAlt(_localctx, 2);
				{
				setState(50); value();
				}
				break;
			case QUOTE:
				enterOuterAlt(_localctx, 3);
				{
				setState(51); quote();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AlignmentContext extends ParserRuleContext {
		public ThrowawayContext throwaway() {
			return getRuleContext(ThrowawayContext.class,0);
		}
		public TerminalNode NUMBER() { return getToken(AMRParser.NUMBER, 0); }
		public TerminalNode QUOTE() { return getToken(AMRParser.QUOTE, 0); }
		public AlignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterAlignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitAlignment(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitAlignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AlignmentContext alignment() throws RecognitionException {
		AlignmentContext _localctx = new AlignmentContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_alignment);
		try {
			setState(70);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(54); match(T__2);
				setState(55); match(NUMBER);
				setState(56); match(T__0);
				setState(57); match(QUOTE);
				setState(58); match(T__1);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(59); match(T__2);
				setState(60); throwaway();
				setState(61); match(T__6);
				setState(62); match(NUMBER);
				setState(63); match(T__0);
				setState(64); match(QUOTE);
				setState(65); match(T__1);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(67); match(T__2);
				setState(68); match(NUMBER);
				setState(69); match(T__1);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ThrowawayContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(AMRParser.NUMBER, 0); }
		public TerminalNode QUOTE() { return getToken(AMRParser.QUOTE, 0); }
		public ThrowawayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_throwaway; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterThrowaway(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitThrowaway(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitThrowaway(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ThrowawayContext throwaway() throws RecognitionException {
		ThrowawayContext _localctx = new ThrowawayContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_throwaway);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(72); match(T__7);
			setState(73); match(NUMBER);
			setState(74); match(T__0);
			setState(75); match(QUOTE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IntroductionContext extends ParserRuleContext {
		public Token entity;
		public List<TerminalNode> LABEL() { return getTokens(AMRParser.LABEL); }
		public TerminalNode LABEL(int i) {
			return getToken(AMRParser.LABEL, i);
		}
		public TerminalNode NUMBER() { return getToken(AMRParser.NUMBER, 0); }
		public IntroductionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_introduction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterIntroduction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitIntroduction(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitIntroduction(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IntroductionContext introduction() throws RecognitionException {
		IntroductionContext _localctx = new IntroductionContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_introduction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(77); match(LABEL);
			setState(78); match(T__8);
			setState(79);
			((IntroductionContext)_localctx).entity = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==LABEL || _la==NUMBER) ) {
				((IntroductionContext)_localctx).entity = (Token)_errHandler.recoverInline(this);
			}
			consume();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ReferenceContext extends ParserRuleContext {
		public TerminalNode LABEL() { return getToken(AMRParser.LABEL, 0); }
		public ReferenceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reference; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterReference(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitReference(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitReference(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReferenceContext reference() throws RecognitionException {
		ReferenceContext _localctx = new ReferenceContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_reference);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(81); match(LABEL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ValueContext extends ParserRuleContext {
		public TerminalNode POLARITY() { return getToken(AMRParser.POLARITY, 0); }
		public TerminalNode NUMBER() { return getToken(AMRParser.NUMBER, 0); }
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_value);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(83);
			_la = _input.LA(1);
			if ( !(_la==NUMBER || _la==POLARITY) ) {
			_errHandler.recoverInline(this);
			}
			consume();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TerminalContext extends ParserRuleContext {
		public TerminalNode LABEL() { return getToken(AMRParser.LABEL, 0); }
		public TerminalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_terminal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterTerminal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitTerminal(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitTerminal(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TerminalContext terminal() throws RecognitionException {
		TerminalContext _localctx = new TerminalContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_terminal);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(85); match(LABEL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QuoteContext extends ParserRuleContext {
		public TerminalNode QUOTE() { return getToken(AMRParser.QUOTE, 0); }
		public QuoteContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_quote; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).enterQuote(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof AMRListener ) ((AMRListener)listener).exitQuote(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof AMRVisitor ) return ((AMRVisitor<? extends T>)visitor).visitQuote(this);
			else return visitor.visitChildren(this);
		}
	}

	public final QuoteContext quote() throws RecognitionException {
		QuoteContext _localctx = new QuoteContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_quote);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(87); match(QUOTE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\3\20\\\4\2\t\2\4\3"+
		"\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t\13"+
		"\4\f\t\f\3\2\3\2\3\2\5\2\34\n\2\3\2\7\2\37\n\2\f\2\16\2\"\13\2\3\2\3\2"+
		"\3\2\3\2\5\2(\n\2\5\2*\n\2\3\3\3\3\3\3\3\3\3\4\3\4\5\4\62\n\4\3\5\3\5"+
		"\3\5\5\5\67\n\5\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3"+
		"\6\3\6\3\6\5\6I\n\6\3\7\3\7\3\7\3\7\3\7\3\b\3\b\3\b\3\b\3\t\3\t\3\n\3"+
		"\n\3\13\3\13\3\f\3\f\3\f\2\2\r\2\4\6\b\n\f\16\20\22\24\26\2\4\3\2\r\16"+
		"\3\2\16\17Y\2)\3\2\2\2\4+\3\2\2\2\6\61\3\2\2\2\b\66\3\2\2\2\nH\3\2\2\2"+
		"\fJ\3\2\2\2\16O\3\2\2\2\20S\3\2\2\2\22U\3\2\2\2\24W\3\2\2\2\26Y\3\2\2"+
		"\2\30\31\7\6\2\2\31\33\5\6\4\2\32\34\5\n\6\2\33\32\3\2\2\2\33\34\3\2\2"+
		"\2\34 \3\2\2\2\35\37\5\4\3\2\36\35\3\2\2\2\37\"\3\2\2\2 \36\3\2\2\2 !"+
		"\3\2\2\2!#\3\2\2\2\" \3\2\2\2#$\7\7\2\2$*\3\2\2\2%\'\5\b\5\2&(\5\n\6\2"+
		"\'&\3\2\2\2\'(\3\2\2\2(*\3\2\2\2)\30\3\2\2\2)%\3\2\2\2*\3\3\2\2\2+,\7"+
		"\b\2\2,-\7\r\2\2-.\5\2\2\2.\5\3\2\2\2/\62\5\16\b\2\60\62\5\20\t\2\61/"+
		"\3\2\2\2\61\60\3\2\2\2\62\7\3\2\2\2\63\67\5\24\13\2\64\67\5\22\n\2\65"+
		"\67\5\26\f\2\66\63\3\2\2\2\66\64\3\2\2\2\66\65\3\2\2\2\67\t\3\2\2\289"+
		"\7\t\2\29:\7\16\2\2:;\7\13\2\2;<\7\f\2\2<I\7\n\2\2=>\7\t\2\2>?\5\f\7\2"+
		"?@\7\5\2\2@A\7\16\2\2AB\7\13\2\2BC\7\f\2\2CD\7\n\2\2DI\3\2\2\2EF\7\t\2"+
		"\2FG\7\16\2\2GI\7\n\2\2H8\3\2\2\2H=\3\2\2\2HE\3\2\2\2I\13\3\2\2\2JK\7"+
		"\4\2\2KL\7\16\2\2LM\7\13\2\2MN\7\f\2\2N\r\3\2\2\2OP\7\r\2\2PQ\7\3\2\2"+
		"QR\t\2\2\2R\17\3\2\2\2ST\7\r\2\2T\21\3\2\2\2UV\t\3\2\2V\23\3\2\2\2WX\7"+
		"\r\2\2X\25\3\2\2\2YZ\7\f\2\2Z\27\3\2\2\2\t\33 \')\61\66H";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}