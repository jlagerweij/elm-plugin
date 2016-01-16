package org.intellij.elmlang.elmplugin;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import org.intellij.elmlang.elmplugin.psi.ElmTypes;

%%

%class ElmLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%state INDENTED, DECLARATION

CRLF= \n|\r|\r\n
WHITE_SPACE=[\ \t\f]
END_OF_LINE_COMMENT=("--")[^\r\n]*
NON_WHITE=[a-z]+

%%

// YYINITIAL state is when the line hasn't had any lexems yet.

<YYINITIAL> {WHITE_SPACE}+ { yybegin(INDENTED); return ElmTypes.INDENTATION; }

<YYINITIAL> {NON_WHITE} { yybegin(DECLARATION); return ElmTypes.IDENTIFIER; }

{CRLF}+ { yybegin(YYINITIAL); return ElmTypes.NEW_LINE; }

{WHITE_SPACE}+ { return TokenType.WHITE_SPACE; }

{END_OF_LINE_COMMENT} { return ElmTypes.COMMENT; }

. { return TokenType.BAD_CHARACTER; }