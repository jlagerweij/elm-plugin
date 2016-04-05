package org.elmlang.intellijplugin.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.elmlang.intellijplugin.psi.*;
import org.elmlang.intellijplugin.psi.references.ElmReference;
import org.elmlang.intellijplugin.psi.references.ElmValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ElmPsiImplUtil {
    public static String getName(ElmUpperCaseId element) {
        return element.getText();
    }

    public static PsiElement setName(ElmUpperCaseId element, String newName) {
        ASTNode node = element.getNode().findChildByType(ElmTypes.UPPER_CASE_IDENTIFIER);
        if (node != null) {
            ElmUpperCaseId id = ElmElementFactory.createUpperCaseId(element.getProject(), newName);
            ASTNode newNode = id.getFirstChild().getNode();
            element.getNode().replaceChild(node, newNode);
        }
        return element;
    }

    public static String getName(ElmLowerCaseId element) {
        return element.getText();
    }

    public static PsiElement setName(ElmLowerCaseId element, String newName) {
        ASTNode node = element.getNode().findChildByType(ElmTypes.UPPER_CASE_IDENTIFIER);
        if (node != null) {
            ElmLowerCaseId id = ElmElementFactory.createLowerCaseId(element.getProject(), newName);
            ASTNode newNode = id.getFirstChild().getNode();
            element.getNode().replaceChild(node, newNode);
        }
        return element;
    }

    public static PsiElement getNameIdentifier(ElmLowerCaseId element) {
        ASTNode node = element.getNode();
        if (node != null) {
            return node.getPsi();
        } else {
            return null;
        }
    }

    public static PsiElement getNameIdentifier(ElmUpperCaseId element) {
        ASTNode node = element.getNode();
        if (node != null) {
            return node.getPsi();
        } else {
            return null;
        }
    }

    public static Stream<ElmReference> getReferencesStream(ElmExpression element) {
        return getReferencesInAncestor(
                element,
                Stream.concat(
                        element.getListOfOperandsList().stream()
                                .map(ElmPsiImplUtil::getReferencesStream),
                        element.getBacktickedFunctionList().stream()
                                .map(ElmPsiImplUtil::getReferencesStream)
                )
        );
    }

    public static Stream<ElmReference> getReferencesStream(ElmBacktickedFunction element) {
        return getReferencesInAncestor(
                element,
                PsiTreeUtil.findChildrenOfAnyType(element, ElmLowerCasePathImpl.class, ElmMixedCasePathImpl.class).stream()
                        .map(ElmPsiElement::getReferencesStream)
        );
    }

    private static Stream<ElmReference> getReferencesInAncestor(PsiElement ancestor, Stream<Stream<ElmReference>> references) {
        return references
                .map(list -> list.map(r -> r.referenceInAncestor(ancestor)))
                .reduce(Stream.empty(), Stream::concat);
    }

    public static Stream<ElmReference> getReferencesStream(ElmListOfOperands element) {
        Stream<Stream<ElmReference>> references = Arrays.stream(element.getChildren())
                .map(child -> {
                    if (child instanceof ElmWithExpression) {
                        return ElmPsiImplUtil.getReferencesStream(((ElmWithExpression) child));
                    } else if (child instanceof ElmWithExpressionList) {
                        return ElmPsiImplUtil.getReferencesStream(((ElmWithExpressionList) child));
                    } else if (child instanceof ElmLowerCasePathImpl) {
                        return ((ElmLowerCasePathImpl) child).getReferencesStream();
                    } else {
                        return Stream.empty();
                    }
                });
        return getReferencesInAncestor(element, references);
    }

    public static Stream<ElmReference> getReferencesStream(ElmWithExpressionList element) {
        return getReferencesInAncestor(
                element,
                element.getExpressionList().stream()
                        .map(ElmPsiImplUtil::getReferencesStream)
        );
    }

    public static Stream<ElmReference> getReferencesStream(ElmWithExpression element) {
        return getReferencesStream(element.getExpression())
                .map(r -> r.referenceInAncestor(element));
    }

    public static Stream<ElmReference> getReferencesStream(ElmRecord record) {

        Stream<ElmReference> recordBase = Optional.ofNullable(record.getLowerCaseId())
                .map(id -> new ElmValueReference(id).referenceInAncestor(record))
                .map(Stream::of)
                .orElse(Stream.empty());

        Stream<ElmReference> fields = record.getFieldList().stream()
                .map(ElmPsiImplUtil::getReferencesStream)
                .reduce(Stream.empty(), Stream::concat);

        return Stream.concat(recordBase, fields);
    }

    @Nullable
    public static ElmUpperCasePath getModuleName(ElmModuleDeclaration module) {
        return PsiTreeUtil.findChildOfType(module, ElmUpperCasePath.class);
    }

    @Nullable
    public static ElmUpperCasePath getModuleName(ElmImportClause module) {
        return PsiTreeUtil.findChildOfType(module, ElmUpperCasePath.class);
    }

    public static boolean isExposingAll(ElmModuleDeclaration element) {
        return isAnyChildDoubleDot(element);
    }

    public static boolean isExposingAll(ElmExposingClause element) {
        return isAnyChildDoubleDot(element);
    }

    private static boolean isAnyChildDoubleDot(PsiElement element) {
        Predicate<PsiElement> predicate = e ->
                e instanceof ASTNode
                        && ((ASTNode)e).getElementType().equals(ElmTypes.DOUBLE_DOT);
        return ElmTreeUtil.isAnyMatchInChildren(element, predicate);
    }

    @NotNull
    public static List<ElmValueDeclarationBase> getValueDeclarations(ElmWithValueDeclarations element) {
        return Arrays.stream(element.getChildren())
                .filter(e -> e instanceof ElmValueDeclarationBase)
                .map(e -> (ElmValueDeclarationBase) e)
                .collect(Collectors.toList());
    }

    @NotNull
    public static List<ElmLowerCaseId> getDeclarationsFromPattern(@Nullable ElmPattern pattern) {
        if (pattern == null) {
            return Collections.emptyList();
        }

        List<ElmLowerCaseId> result = new LinkedList<>();

        result.addAll(pattern.getLowerCaseIdList());

        addDeclarationsToResult(
                result,
                pattern.getListPatternList(),
                ElmPsiImplUtil::getDeclarationsFromParentPattern);

        addDeclarationsToResult(
                result,
                pattern.getParenthesedPatternList(),
                p -> getDeclarationsFromPattern(p.getPattern()));

        addDeclarationsToResult(
                result,
                pattern.getRecordPatternList(),
                ElmRecordPattern::getLowerCaseIdList);

        addDeclarationsToResult(
                result,
                pattern.getTuplePatternList(),
                ElmPsiImplUtil::getDeclarationsFromParentPattern);

        addDeclarationsToResult(
                result,
                pattern.getUnionPatternList(),
                ElmPsiImplUtil::getDeclarationsFromParentPattern);

        return result;
    }

    private static <T> void addDeclarationsToResult(List<ElmLowerCaseId> result, List<T> source, Function<T, List<ElmLowerCaseId>> f) {
        source.stream()
                .map(f)
                .forEach(result::addAll);
    }

    private static List<ElmLowerCaseId> getDeclarationsFromParentPattern(ElmWithPatternList parentPattern) {
        List<ElmLowerCaseId> result = new LinkedList<>();
        parentPattern.getPatternList().stream()
                .map(ElmPsiImplUtil::getDeclarationsFromPattern)
                .forEach(result::addAll);
        return result;
    }

    public static List<ElmLowerCaseId> getDefinedValues(ElmValueDeclarationBase element) {
        List<ElmLowerCaseId> result = new LinkedList<>();
        Arrays.stream(element.getChildren())
                .map(child -> {
                    if (child instanceof ElmPattern) {
                        return getDeclarationsFromPattern((ElmPattern) child);
                    } else if (child instanceof ElmWithSingleId) {
                        return Collections.singletonList(((ElmWithSingleId) child).getLowerCaseId());
                    } else {
                        return Collections.<ElmLowerCaseId>emptyList();
                    }
                })
                .forEach(result::addAll);
        return result;
    }
}