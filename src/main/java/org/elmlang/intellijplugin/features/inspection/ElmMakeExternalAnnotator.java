package org.elmlang.intellijplugin.features.inspection;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import org.elmlang.intellijplugin.Component;
import org.elmlang.intellijplugin.elmmake.ElmMake;
import org.elmlang.intellijplugin.settings.ElmPluginSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ElmMakeExternalAnnotator extends ExternalAnnotator<PsiFile, List<ElmMakeExternalAnnotator.ElmMakeResult>> {
    private static final Logger LOG = Logger.getInstance(Component.class);
    private static final String TAB = "    ";

    static class ElmMakeResult {
        String tag;
        String overview;
        Region subregion;
        String details;
        Region region;
        String type;
        String file;
    }

    static class Region {
        Position start;
        Position end;
    }

    static class Position {
        int line;
        int column;
    }

    @Nullable
    @Override
    public PsiFile collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return collectInformation(file);
    }

    /** Called first; in our case, just return file and do nothing */
    @Override
    @Nullable
    public PsiFile collectInformation(@NotNull PsiFile file) {
        return file;
    }

    /** Called 2nd; look for trouble in file and return list of issues.
     *
     *  For most custom languages, you would not reimplement your semantic
     *  analyzer using PSI trees. Instead, here is where you would call out to
     *  your custom languages compiler or interpreter to get error messages
     *  or other bits of information you'd like to annotate the document with.
     */
    @Nullable
    @Override
    public List<ElmMakeResult> doAnnotate(final PsiFile file) {
        ElmPluginSettings elmPluginSettings = ElmPluginSettings.getInstance(file.getProject());

        if (!elmPluginSettings.pluginEnabled) {
            return Collections.emptyList();
        }
        if (!isValidPsiFile(file)) {
            return Collections.emptyList();
        }

        final Optional<String> basePath = findElmPackageDirectory(file);

        if (!basePath.isPresent()) {
            return Collections.emptyList();
        }

        String canonicalPath = file.getVirtualFile().getCanonicalPath();

        Optional<InputStream> inputStream = ElmMake.execute(basePath.get(), elmPluginSettings.elmMakeExecutable, canonicalPath);

        List<ElmMakeResult> issues = new ArrayList<>();
        if (inputStream.isPresent()) {
            String output = null;
            try {
                List<String> lines = CharStreams.readLines(new InputStreamReader(inputStream.get()));
                for (String line : lines) {
                    if (notValidJsonArray(line)) {
                        continue;
                    }
                    output = line;
                    List<ElmMakeResult> makeResult = new Gson().fromJson(output, new TypeToken<List<ElmMakeResult>>() {
                    }.getType());

                    issues.addAll(makeResult
                            .stream()
                            .filter(res -> isIssueForCurrentFile(basePath.get(), canonicalPath, res)).collect(Collectors.toList()));
                }
            } catch (JsonSyntaxException e) {
                LOG.error(e.getMessage(), e);
                LOG.error("Could not convert to JSON: " + output);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
        }

        return issues;
    }

    private Optional<String> findElmPackageDirectory(PsiFile file) {
        final PsiDirectory[] parent = new PsiDirectory[1];
        ApplicationManager.getApplication().runReadAction(() -> {
            parent[0] = file.getParent();
            while (parent[0] != null && parent[0].isValid() && parent[0].findFile("elm-package.json") == null) {
                parent[0] = parent[0].getParent();
            }

        });

        if (parent[0] == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(parent[0].getVirtualFile().getCanonicalPath());
        }
    }

    private boolean notValidJsonArray(String line) {
        return !line.startsWith("[");
    }

    private boolean isIssueForCurrentFile(String basePath, String canonicalPath, ElmMakeResult res) {
        return res.file.replace("./", basePath + "/").equals(canonicalPath);
    }

    /** Called 3rd to actually annotate the editor window */
    @Override
    public void apply(@NotNull PsiFile file,
                      List<ElmMakeResult> issues,
                      @NotNull AnnotationHolder holder) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);

        for (ElmMakeResult issue : issues) {
            annotateForIssue(holder, document, issue, file);
        }
    }

    private void annotateForIssue(@NotNull AnnotationHolder holder, Document document, ElmMakeResult issue, PsiFile file) {
        TextRange selector = findAnnotationLocation(document, issue);

        Annotation annotation;
        if (issue.type.equals("warning")) {
            if (issue.tag.equals("unused import")) {
                holder.createWeakWarningAnnotation(selector, null);
                annotation = holder.createWeakWarningAnnotation(selector, issue.overview);
                annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            } else {
                annotation = holder.createWarningAnnotation(selector, issue.overview);
            }
        } else {
            annotation = holder.createErrorAnnotation(selector, issue.overview);

            WolfTheProblemSolver theProblemSolver = WolfTheProblemSolver.getInstance(file.getProject());
            final Problem problem = theProblemSolver.convertToProblem(file.getVirtualFile(), issue.region.start.line, issue.region.start.column, new String[]{ issue.details });
            theProblemSolver.weHaveGotNonIgnorableProblems(file.getVirtualFile(), Collections.singletonList(problem));
        }

        String tooltip = createToolTip(issue);
        annotation.setTooltip(tooltip);
    }

    @NotNull
    private TextRange findAnnotationLocation(Document document, ElmMakeResult issue) {
        Region region = issue.subregion != null ? issue.subregion : issue.region;

        int offsetStart = StringUtil.lineColToOffset(document.getText(), region.start.line - 1, region.start.column - 1);
        int offsetEnd = StringUtil.lineColToOffset(document.getText(), region.end.line - 1, region.end.column - 1);

        if (isMultiLineRegion(region)) {
            offsetEnd = document.getLineEndOffset(region.start.line - 1);
        }
        return new TextRange(offsetStart, offsetEnd);
    }

    private boolean isMultiLineRegion(Region region) {
        return region.start.line != region.end.line;
    }

    private boolean isValidPsiFile(PsiFile file) {
        return file != null && file.getVirtualFile() != null && file.getVirtualFile().isValid();
    }

    @NotNull
    private String createToolTip(ElmMakeResult issue) {
        String previousLine = "";
        StringBuilder tooltip = new StringBuilder("<html><strong>" + issue.overview + "</strong><br/><hr/>");
        String[] lines = issue.details.split("\\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (!previousLine.startsWith(TAB) && line.startsWith(TAB)) {
                tooltip.append("<pre style=\"font-weight:bold;\">");
            } else if (previousLine.startsWith(TAB) && !line.startsWith(TAB)) {
                tooltip.append("</pre>");
            }
            if (line.startsWith(TAB)) {
                tooltip.append(line).append("\n");
            } else {
                tooltip.append(line).append("<br/>");
            }
            previousLine = line;
        }
        tooltip.append("</html>");
        return tooltip.toString();
    }

}

