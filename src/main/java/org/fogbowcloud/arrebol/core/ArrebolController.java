package org.fogbowcloud.arrebol.core;

import org.fogbowcloud.arrebol.core.models.job.Job;
import org.fogbowcloud.arrebol.core.models.job.JobSpec;
import org.fogbowcloud.arrebol.core.models.job.JobState;
import org.fogbowcloud.arrebol.core.models.task.Task;
import org.fogbowcloud.arrebol.core.models.task.TaskState;
import org.fogbowcloud.arrebol.core.monitors.TasksMonitor;
import org.fogbowcloud.arrebol.core.scheduler.StandardScheduler;
import org.fogbowcloud.arrebol.core.scheduler.Scheduler;
import org.fogbowcloud.arrebol.core.resource.ResourceObserver;
import org.fogbowcloud.arrebol.core.resource.ResourceManager;
import org.fogbowcloud.arrebol.pools.resource.ResourcePool;
import org.fogbowcloud.arrebol.pools.resource.ResourceStateTransitioner;

import java.util.Map;
import java.util.Properties;


public class ArrebolController {

    private Scheduler scheduler;
    private TasksMonitor tasksMonitor;
    private Properties properties;

    private ResourceManager resourceManager;

    public ArrebolController(Properties properties) {
        this.properties = properties;
        this.resourceManager = new ResourceManager();

        ResourceStateTransitioner resourceStateTransitioner = this.resourceManager.getResourcePool();
        this.tasksMonitor = new TasksMonitor(resourceStateTransitioner);
        this.scheduler = new StandardScheduler(this.tasksMonitor);

        ResourceObserver schedulerObserver = (ResourceObserver) this.scheduler;
        this.resourceManager.registerObserver(schedulerObserver);
    }

    public void start() {
        // TODO: read from bd

        this.tasksMonitor.start();
    }

    public void stop() {
        // TODO: delete all resources

        this.tasksMonitor.stop();
    }

    public String addJob(Job job) {
        Map<String, Task> taskMap = job.getTasks();
        for(Task task : taskMap.values()){
            this.scheduler.addTask(task);
        }
        job.setJobState(JobState.READY);
        return job.getId();
    }

    public String stopJob(Job job) {
        Map<String, Task> taskMap = job.getTasks();
        for(Task task : taskMap.values()){
            this.scheduler.stopTask(task);
        }
        return job.getId();
    }

    public TaskState getTaskState(String taskId) {
        Task task = this.tasksMonitor.getTaskById(taskId);
        TaskState taskState = this.tasksMonitor.getTaskState(task);
        return taskState;
    }
}
