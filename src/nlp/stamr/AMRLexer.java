// Generated from /home/keenon/Desktop/principled/src/main/antlr4/edu/stanford/nlp/stamr/AMR.g4 by ANTLR 4.4.1-dev
package nlp.stamr;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class AMRLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.4.1-dev", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__8=1, T__7=2, T__6=3, T__5=4, T__4=5, T__3=6, T__2=7, T__1=8, T__0=9, 
		QUOTE=10, LABEL=11, NUMBER=12, POLARITY=13, WHITESPACE=14;
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] tokenNames = {
		"'\\u0000'", "'\\u0001'", "'\\u0002'", "'\\u0003'", "'\\u0004'", "'\\u0005'", 
		"'\\u0006'", "'\\u0007'", "'\b'", "'\t'", "'\n'", "'\\u000B'", "'\f'", 
		"'\r'", "'\\u000E'"
	};
	public static final String[] ruleNames = {
		"T__8", "T__7", "T__6", "T__5", "T__4", "T__3", "T__2", "T__1", "T__0", 
		"QUOTE", "LABEL", "NUMBER", "POLARITY", "WHITESPACE"
	};


	public AMRLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "AMR.g4"; }

	@Override
	public String[] getTokenNames() { return tokenNames; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2\20c\b\1\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3"+
		"\3\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t"+
		"\3\n\3\n\3\13\3\13\6\13=\n\13\r\13\16\13>\3\13\3\13\3\f\6\fD\n\f\r\f\16"+
		"\fE\3\f\7\fI\n\f\f\f\16\fL\13\f\3\f\5\fO\n\f\3\r\6\rR\n\r\r\r\16\rS\3"+
		"\r\3\r\6\rX\n\r\r\r\16\rY\5\r\\\n\r\3\16\3\16\3\17\3\17\3\17\3\17\2\2"+
		"\20\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35"+
		"\20\3\2\b\3\2$$\4\2C\\c|\7\2))//\62;C\\c|\3\2\62;\4\2--//\5\2\13\f\17"+
		"\17\"\"i\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2"+
		"\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27"+
		"\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\3\37\3\2\2\2\5!\3\2\2"+
		"\2\7\'\3\2\2\2\t.\3\2\2\2\13\60\3\2\2\2\r\62\3\2\2\2\17\64\3\2\2\2\21"+
		"\66\3\2\2\2\238\3\2\2\2\25:\3\2\2\2\27C\3\2\2\2\31Q\3\2\2\2\33]\3\2\2"+
		"\2\35_\3\2\2\2\37 \7\61\2\2 \4\3\2\2\2!\"\7I\2\2\"#\7W\2\2#$\7G\2\2$%"+
		"\7U\2\2%&\7U\2\2&\6\3\2\2\2\'(\7C\2\2()\7E\2\2)*\7V\2\2*+\7W\2\2+,\7C"+
		"\2\2,-\7N\2\2-\b\3\2\2\2./\7*\2\2/\n\3\2\2\2\60\61\7+\2\2\61\f\3\2\2\2"+
		"\62\63\7<\2\2\63\16\3\2\2\2\64\65\7]\2\2\65\20\3\2\2\2\66\67\7_\2\2\67"+
		"\22\3\2\2\289\7?\2\29\24\3\2\2\2:<\7$\2\2;=\n\2\2\2<;\3\2\2\2=>\3\2\2"+
		"\2><\3\2\2\2>?\3\2\2\2?@\3\2\2\2@A\7$\2\2A\26\3\2\2\2BD\t\3\2\2CB\3\2"+
		"\2\2DE\3\2\2\2EC\3\2\2\2EF\3\2\2\2FJ\3\2\2\2GI\t\4\2\2HG\3\2\2\2IL\3\2"+
		"\2\2JH\3\2\2\2JK\3\2\2\2KN\3\2\2\2LJ\3\2\2\2MO\7)\2\2NM\3\2\2\2NO\3\2"+
		"\2\2O\30\3\2\2\2PR\t\5\2\2QP\3\2\2\2RS\3\2\2\2SQ\3\2\2\2ST\3\2\2\2T[\3"+
		"\2\2\2UW\7\60\2\2VX\t\5\2\2WV\3\2\2\2XY\3\2\2\2YW\3\2\2\2YZ\3\2\2\2Z\\"+
		"\3\2\2\2[U\3\2\2\2[\\\3\2\2\2\\\32\3\2\2\2]^\t\6\2\2^\34\3\2\2\2_`\t\7"+
		"\2\2`a\3\2\2\2ab\b\17\2\2b\36\3\2\2\2\n\2>EJNSY[\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}