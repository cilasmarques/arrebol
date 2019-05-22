package org.fogbowcloud.arrebol.execution;

import org.apache.log4j.Logger;
import org.fogbowcloud.arrebol.execution.constans.DockerConstants;
import org.fogbowcloud.arrebol.execution.exceptions.DockerStartException;
import org.fogbowcloud.arrebol.execution.dockerworker.ExecInstanceResult;
import org.fogbowcloud.arrebol.execution.dockerworker.WorkerDockerRequestHelper;
import org.fogbowcloud.arrebol.models.command.Command;
import org.fogbowcloud.arrebol.models.command.CommandState;
import org.fogbowcloud.arrebol.models.task.Task;
import org.fogbowcloud.arrebol.models.task.TaskSpec;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;

public class DockerTaskExecutor implements TaskExecutor {

    private String imageId;
    private String containerName;


    private WorkerDockerRequestHelper workerDockerRequestHelper;
    private final Logger LOGGER = Logger.getLogger(DockerTaskExecutor.class);

    public DockerTaskExecutor(String imageId, String containerName, String address) {
        this.imageId = imageId;
        this.containerName = containerName;
        this.workerDockerRequestHelper = new WorkerDockerRequestHelper(address, containerName, imageId);
    }

    @Override
    public TaskExecutionResult execute(Task task){
        //FIXME: We should catch the errors when starting/finishing the container and move the task to its FAILURE state
        //FIXME: also, follow the SAME log format we used in the RawTaskExecutor
        TaskExecutionResult taskExecutionResult;

        Integer startStatus = this.start();

        updateRequirements(task.getTaskSpec());

        if(startStatus != 0){
            LOGGER.error("Exit code from container start: " + startStatus);
            throw new DockerStartException("Could not start container " + getContainerName());
        }

        LOGGER.info("Successful started container " + getContainerName());
        Command[] commands = getCommands(task);
        LOGGER.info("Starting to execute commands [" + commands.length + "] of task " + task.getId());
        int[] commandsResults = executeCommands(commands);

        Integer stopStatus = this.stop();
        if(stopStatus != 0){
            LOGGER.error("Exit code from container stop: " + stopStatus);
        }

        taskExecutionResult = getTaskResult(commands, commandsResults);

        LOGGER.info("Result of task [" + task.getId() + "]: " + taskExecutionResult.getResult().toString());
        return taskExecutionResult;
    }

    private void updateRequirements(TaskSpec taskSpec){
        verifyImage(taskSpec);
        Map<String, String> mapRequirements = new HashMap<>();
        String dockerRequirements = taskSpec.getSpec().getRequirements().get(DockerConstants.METADATA_DOCKER_REQUIREMENTS);
        if(dockerRequirements != null){
            String requirements[] = dockerRequirements.split("&&");
            for(String requirement : requirements){
                String req[] = requirement.split("==");
                String key = req[0].trim();
                String value = req[1].trim();
                switch (key){
                    case DockerConstants.DOCKER_MEMORY:
                        mapRequirements.put(DockerConstants.JSON_KEY_MEMORY, value);
                        LOGGER.info("Added requirement ["+DockerConstants.JSON_KEY_MEMORY+"] with value ["+value+"]");
                        break;
                    case DockerConstants.DOCKER_CPU_WEIGHT:
                        mapRequirements.put(DockerConstants.JSON_KEY_CPU_SHARES, value);
                        LOGGER.info("Added requirement ["+DockerConstants.JSON_KEY_CPU_SHARES+"] with value ["+value+"]");
                        break;
                }
            }
            this.workerDockerRequestHelper.setRequirements(mapRequirements);
        }
    }

    private void verifyImage(TaskSpec taskSpec){
        String image = taskSpec.getImage();
        if(image != null && !image.trim().isEmpty()){
            LOGGER.info("Using image ["+ image +"] to start " +containerName);
            this.setImage(image);
        } else {
            LOGGER.info("Using default image ["+ DockerVariable.DEFAULT_IMAGE + "] to start " + containerName);
            this.setImage(DockerVariable.DEFAULT_IMAGE);
        }
    }

    protected Command[] getCommands(Task task){
        TaskSpec taskSpec = task.getTaskSpec();
        List<Command> commandsList = taskSpec.getCommands();

        int commandsSize = commandsList.size();
        Command[] commands = commandsList.toArray(new Command[commandsSize]);
        return commands;
    }

    protected int[] executeCommands(Command[] commands){
        int[] commandsResults = new int[commands.length];
        Arrays.fill(commandsResults, TaskExecutionResult.UNDETERMINED_RESULT);
        for (int i = 0; i < commands.length; i++) {
            Command c = commands[i];
            c.setState(CommandState.RUNNING);
            try {
                Integer exitCode = executeCommand(c);
                commandsResults[i] = exitCode;
                c.setState(CommandState.FINISHED);
            } catch (Throwable t) {
                c.setState(CommandState.FAILED);
            }

        }
        return commandsResults;
    }

    protected TaskExecutionResult getTaskResult(Command[] commands, int[] commandsResults){
        TaskExecutionResult taskExecutionResult;
        TaskExecutionResult.RESULT result = TaskExecutionResult.RESULT.SUCCESS;
        for (Command cmd : commands) {
            if (cmd.getState().equals(CommandState.FAILED)) {
                result = TaskExecutionResult.RESULT.FAILURE;
                break;
            }
        }
        taskExecutionResult = new TaskExecutionResult(result, commandsResults, commands);
        return taskExecutionResult;
    }

    protected Integer start(){
        try {
            LOGGER.info("Starting DockerTaskExecutor " + this.getContainerName());
            this.workerDockerRequestHelper.start();
            return new Integer(0);
        } catch (Exception e) {
            return new Integer(127);
        }
    }

    protected Integer stop(){
        try {
            LOGGER.info("Stopping DockerTaskExecutor " + this.getContainerName());
            this.workerDockerRequestHelper.stop();
            return new Integer(0);
        } catch (Exception e) {
            e.printStackTrace();
            return new Integer(127);
        }
    }

    protected Integer executeCommand(Command command){
        try {
            LOGGER.info("Executing command [" + command.getCommand() + "] in worker [" + this.getContainerName() + "].");
            String execId = this.workerDockerRequestHelper.createExecInstance(command.getCommand());
            this.workerDockerRequestHelper.startExecInstance(execId);
            ExecInstanceResult execInstanceResult = this.workerDockerRequestHelper.inspectExecInstance(execId);
            while(execInstanceResult.getExitCode() == null){
                execInstanceResult = this.workerDockerRequestHelper.inspectExecInstance(execId);
                try {
                    sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LOGGER.info("Executed command [" + command.getCommand() + "] with exitcode=[" + execInstanceResult.getExitCode() + "] in worker [" + this.getContainerName() + "].");
            return execInstanceResult.getExitCode();
        } catch(Exception e){
            e.printStackTrace();
            return new Integer(127);
        }
    }

    private void setImage(String imageId){
        this.imageId = imageId;
        this.workerDockerRequestHelper.setImage(imageId);
    }

    @Override
    public String toString() {
        return "DockerTaskExecutor imageId={" + getImageId() + "} containerName={" + getContainerName() + "}";
    }

    public String getContainerName(){
        return this.containerName;
    }

    public String getImageId(){
        return this.imageId;
    }


}
