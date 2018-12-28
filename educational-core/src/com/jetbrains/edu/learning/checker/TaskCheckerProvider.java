package com.jetbrains.edu.learning.checker;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.courseFormat.tasks.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TaskCheckerProvider {
    @NotNull
    TaskChecker<EduTask> getEduTaskChecker(@NotNull EduTask task, @NotNull Project project);

    @NotNull
    default OutputTaskChecker getOutputTaskChecker(@NotNull OutputTask task, @NotNull Project project) {
        return new OutputTaskChecker(task, project);
    }

    @NotNull
    default TheoryTaskChecker getTheoryTaskChecker(@NotNull TheoryTask task, @NotNull Project project) {
        return new TheoryTaskChecker(task, project);
    }

    @Nullable
    default TaskChecker<ChoiceTask> getChoiceTaskChecker(@NotNull ChoiceTask task, @NotNull Project project) {
        return null;
    }

    @Nullable
    default TaskChecker<CodeTask> getCodeTaskChecker(@NotNull CodeTask task, @NotNull Project project) {
        return null;
    }

    @NotNull
    default TaskChecker<IdeTask> getIdeTaskChecker(@NotNull IdeTask task, @NotNull Project project) {
        return new IdeTaskChecker(task, project);
    }

    @Nullable
    default TaskChecker getTaskChecker(@NotNull Task task, @NotNull Project project) {
        if (task instanceof EduTask) {
            return getEduTaskChecker((EduTask) task, project);
        }
        else if (task instanceof OutputTask) {
            return getOutputTaskChecker((OutputTask) task, project);
        }
        else if (task instanceof TheoryTask) {
            return getTheoryTaskChecker((TheoryTask) task, project);
        }
        else if (task instanceof CodeTask) {
            return getCodeTaskChecker((CodeTask) task, project);
        }
        else if (task instanceof ChoiceTask) {
            return getChoiceTaskChecker((ChoiceTask) task, project);
        }
        else if (task instanceof IdeTask) {
            return getIdeTaskChecker((IdeTask) task, project);
        }
        else {
            throw new IllegalStateException("Unknown task type: " + task.getTaskType());
        }
    }
}
