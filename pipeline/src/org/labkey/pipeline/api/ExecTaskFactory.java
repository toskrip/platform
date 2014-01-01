package org.labkey.pipeline.api;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.XMLBeanTaskFactoryFactory;
import org.labkey.api.pipeline.cmd.ExeToCommandArgs;
import org.labkey.api.pipeline.cmd.JobParamToCommandArgs;
import org.labkey.api.pipeline.cmd.ListToCommandArgs;
import org.labkey.api.pipeline.cmd.PathInLine;
import org.labkey.api.pipeline.cmd.RequiredInLine;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.cmd.TaskToCommandArgs;
import org.labkey.api.pipeline.cmd.ValueInLine;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.pipeline.analysis.CommandTaskImpl;
import org.labkey.pipeline.xml.ExecTaskType;
import org.labkey.pipeline.xml.ExecType;
import org.labkey.pipeline.xml.TaskType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/30/13
 */
public class ExecTaskFactory extends SimpleTaskFactory
{

    public ExecTaskFactory(TaskId taskId)
    {
        super(taskId);
        setStatusName(taskId.getName());
    }

    public static class FactoryFactory implements XMLBeanTaskFactoryFactory
    {
        @Override
        public TaskFactory create(TaskId taskId, TaskType xobj, Path tasksDir)
        {
            if (!(xobj instanceof ExecTaskType))
                throw new IllegalArgumentException("XML instance must be a ExecTaskType");

            return ExecTaskFactory.create(taskId, (ExecTaskType)xobj, tasksDir);
        }
    }

    private static ExecTaskFactory create(TaskId taskId, ExecTaskType xtask, Path tasksDir)
    {
        ExecTaskFactory factory = new ExecTaskFactory(taskId);

        SimpleTaskFactory.create(factory, xtask, tasksDir);

        Module module = ModuleLoader.getInstance().getModule(taskId.getModuleName());

        ListToCommandArgs converter = createExecConverter(factory, module, tasksDir, xtask.getExec());
        factory.setConverter(converter);

        return factory;
    }

    private static ListToCommandArgs createExecConverter(ExecTaskFactory factory, Module module, Path taskDir, ExecType xexec)
    {
        if (xexec == null)
            throw new IllegalArgumentException("Script task requires <script> element");

        Map<String, TaskPath> inputs = factory.getInputPaths();
        Map<String, TaskPath> outputs = factory.getOutputPaths();
        Map<String, JobParamToCommandArgs> params = factory._params;

        ListToCommandArgs args = new ListToCommandArgs();

        String exeName = xexec.getExe();
        String command = xexec.getStringValue();
        if (command != null)
            command = command.trim();

        List<TaskToCommandArgs> converters = parseCommand(module, taskDir, command, exeName, inputs, params, outputs);
        args.setConverters(converters);

        return args;
    }

    /**
     * Parse the command
     * New inputs and outputs may be created if the command contains references that don't exist yet.
     * References to files within the module are converted into module relative paths which will be resolved
     * to an absolute path by CommandTaskImpl.getProcessPaths() at execution time.
     */
    private static List<TaskToCommandArgs> parseCommand(
            Module module, Path taskDir,
            @NotNull String command, @Nullable String exeName,
            Map<String, TaskPath> inputs,
            Map<String, JobParamToCommandArgs> params,
            Map<String, TaskPath> outputs)
    {
        List<TaskToCommandArgs> ret = new ArrayList<>();

        // TODO: Better parsing: handle quoting and whitespace in tokens
        String[] parts = command.split(" ");
        for (int i = 0; i < parts.length; i++)
        {
            String part = parts[i];
            part = part.trim();
            if (part.length() == 0)
                continue;

            TaskToCommandArgs arg = null;

            if (part.startsWith("${") && part.endsWith("}"))
            {
                //
                // Token replacement
                //

                String key = part.substring("${".length(), part.length() - "}".length());
                if (exeName != null && (key.equals("exe") || key.equals(exeName)))
                {
                    ExeToCommandArgs exe = new ExeToCommandArgs();
                    exe.setExePath(exeName);
                    arg = exe;
                }
                else if (inputs.containsKey(key))
                {
                    TaskPath taskPath = inputs.get(key);
                    // TODO: Get help, switch, optional/required settings
                    PathInLine path = new PathInLine();
                    path.setFunction(WorkDirectory.Function.input);
                    path.setKey(key);
                    arg = path;
                }
                else if (outputs.containsKey(key))
                {
                    TaskPath taskPath = outputs.get(key);
                    PathInLine path = new PathInLine();
                    path.setFunction(WorkDirectory.Function.output);
                    path.setKey(key);
                    arg = path;
                }
                else if (params.containsKey(key))
                {
                    arg = params.get(key);
                }
                else
                {
                    //
                    // Unknown token
                    //

                    if (key.startsWith("input"))
                    {
                        // Attempt to resolve unknown input
                        arg = parseUnknownInput(inputs, key);
                    }
                    else if (key.startsWith("output"))
                    {
                        // Attempt to resolve unknown output
                        arg = parseUnknownOutput(outputs, key);
                    }

                    if (arg == null)
                    {
                        // Not found in inputs, outputs, or params and doesn't start with "input" or "output".
                        // Treat as a unknown parameter
                        ValueInLine param = new ValueInLine();
                        param.setParameter(key);
                        params.put(key, param);
                        arg = param;
                    }
                }
            }
            else
            {
                //
                // String literal
                //

                // Attempt to resolve the token to a resource within the module.
                // For example, if "script.R" resolves to "pipeline/tasks/script.R", add it as a PathInLine argument.
                Resource r = findResource(module, taskDir, part);

                // If we don't have an exe name yet, the first token MUST be the executable name
                if (exeName == null)
                {
                    // CONSIDER: If we resolved the part to a resource in the module, use it as the executable name.
                    exeName = part;
                    ExeToCommandArgs exe = new ExeToCommandArgs();
                    exe.setExePath(exeName);
                    arg = exe;
                }
                else
                {
                    if (r != null)
                    {
                        // Add a refernce to the path relative from the module root.
                        PathInLine p = new PathInLine();
                        p.setFunction(WorkDirectory.Function.module);
                        p.setKey(r.getPath().toString());
                        arg = p;
                    }
                    else
                    {
                        // Add the part as a literal string.
                        RequiredInLine literal = new RequiredInLine();
                        literal.setValue(part);
                        arg = literal;
                    }
                }
            }

            if (arg != null)
                ret.add(arg);
        }

        return ret;
    }

    @Override
    public CommandTaskImpl createTask(PipelineJob job)
    {
        return new CommandTaskImpl(job, this);
    }
}
