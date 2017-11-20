package com.jetbrains.edu.kotlin.studio;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.intellij.JdkProjectSettings;
import com.jetbrains.edu.learning.intellij.generation.CourseModuleBuilder;
import com.jetbrains.edu.learning.intellij.generation.EduGradleModuleGenerator;
import com.jetbrains.edu.learning.intellij.generation.IntellijCourseProjectGeneratorBase;
import com.jetbrains.edu.learning.stepic.StepicConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class KtProjectGenerator extends IntellijCourseProjectGeneratorBase {

  private static final Logger LOG = Logger.getInstance(KtProjectGenerator.class);

  public KtProjectGenerator(@NotNull Course course) {
    super(course);
  }

  @Override
  public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir,
                              @NotNull JdkProjectSettings settings, @NotNull Module module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        StudyTaskManager.getInstance(project).setCourse(myCourse);
        if (CCUtils.isCourseCreator(project)) {
          Lesson lesson = new CCCreateLesson().createAndInitItem(myCourse, null, EduNames.LESSON + 1, 1);
          Task task = new CCCreateTask().createAndInitItem(myCourse, lesson, EduNames.TASK + 1, 1);
          lesson.addTask(task);
          myCourse.getLessons(true).add(lesson);
          KtCourseBuilder.initTask(task);
        }
        Course course = myCourse;
        if (course instanceof RemoteCourse) {
          course = StepicConnector.getCourse(project, (RemoteCourse) course);
          if (course == null) {
            LOG.error("Failed to get course from stepik");
            return;
          }
        }
        course.initCourse(false);
        EduGradleModuleGenerator.createCourseContent(project, course, baseDir.getPath());
      } catch (IOException e) {
        LOG.error("Failed to generate course", e);
      }
    });
    setJdk(project, settings);
  }

  @Nullable
  @Override
  protected CourseModuleBuilder studyModuleBuilder() {
    return null;
  }
}
