/*
 * Copyright (c) 2007-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.action;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.ContextualRoles;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresAllOf;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ForbiddenProjectException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.TermsOfUseException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.AbstractPropertyAccessor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingErrorProcessor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Validator;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: May 16, 2007
 * Time: 1:48:01 PM
 */
public abstract class BaseViewAction<FORM> implements Controller, HasViewContext, Validator,  HasPageConfig, PermissionCheckable
{
    protected static final Logger logger = Logger.getLogger(BaseViewAction.class);
    private ViewContext _context = null;
    private PageConfig _pageConfig = null;
    private PropertyValues _pvs;
    private boolean _robot = false;  // Is this request from GoogleBot or some other crawler?
    private boolean _debug = false;

    protected UnauthorizedException.Type _unauthorizedType = UnauthorizedException.Type.redirectToLogin;
    protected boolean _print = false;
    protected Class _commandClass;
    protected String _commandName = "form";
    protected String[] _supportedMethods = {"GET", "HEAD", "POST"};

    protected BaseViewAction()
    {
        String methodName = getCommandClassMethodName();

        if (null == methodName)
            return;

        // inspect to find form class
        Class typeBest = null;
        for (Method m : this.getClass().getMethods())
        {
            if (methodName.equals(m.getName()))
            {
                Class[] types = m.getParameterTypes();
                if (types.length < 1)
                    continue;
                Class typeCurrent = types[0];
                if (Object.class.equals(typeCurrent))
                    continue;
                assert null == getCommandClass() || typeCurrent.equals(getCommandClass());

                // Using templated classes to extend a base action can lead to multiple
                // versions of a method with acceptable types, so take the most extended
                // type we can find.
                if (typeBest == null || typeBest.isAssignableFrom(typeCurrent))
                    typeBest = typeCurrent;
            }
        }
        if (typeBest != null)
            setCommandClass(typeBest);
    }


    protected abstract String getCommandClassMethodName();


    protected BaseViewAction(Class<? extends FORM> commandClass)
    {
        setCommandClass(commandClass);
    }


    protected void setUnauthorizedType(UnauthorizedException.Type unauthorizedType)
    {
        _unauthorizedType = unauthorizedType;
    }


    public void setProperties(PropertyValues pvs)
    {
        _pvs = pvs;
    }


    public void setProperties(Map m)
    {
        _pvs = new MutablePropertyValues(m);
    }

    /* Doesn't guarantee non-null, non-empty */
    public Object getProperty(String key, String d)
    {
        PropertyValue pv = _pvs.getPropertyValue(key);
        return pv == null ? d : pv.getValue();
    }

    public Object getProperty(Enum key)
    {
        PropertyValue pv = _pvs.getPropertyValue(key.name());
        return pv == null ? null : pv.getValue();
    }

    public Object getProperty(String key)
    {
        PropertyValue pv = _pvs.getPropertyValue(key);
        return pv == null ? null : pv.getValue();
    }


    public PropertyValues getPropertyValues()
    {
        return _pvs;
    }


    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (null == getPropertyValues())
            setProperties(new ServletRequestParameterPropertyValues(request));
        _context.setBindPropertyValues(getPropertyValues());
        handleSpecialProperties();

        return handleRequest();
    }


    private void handleSpecialProperties()
    {
        _robot = PageFlowUtil.isRobotUserAgent(getViewContext().getRequest().getHeader("User-Agent"));

        // Special flag puts actions in "debug" mode, during which they should log extra information that would be
        // helpful for testing or debugging problems
        if (!_robot && null != StringUtils.trimToNull((String) getProperty("_debug")))
        {
            _debug = true;
        }

        if (null != StringUtils.trimToNull((String) getProperty("_print")) ||
            null != StringUtils.trimToNull((String) getProperty("_print.x")))
        {
            _print = true;
        }
    }


    public abstract ModelAndView handleRequest() throws Exception;


    public ViewContext getViewContext()
    {
        return _context;
    }


    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    public void setPageConfig(PageConfig page)
    {
        _pageConfig = page;
    }


    public Container getContainer()
    {
        return getViewContext().getContainer();
    }


    public User getUser()
    {
        return getViewContext().getUser();
    }


    public PageConfig getPageConfig()
    {
        return _pageConfig;
    }


    public void setTitle(String title)
    {
        assert null != getPageConfig() : "action not initialized property";
        getPageConfig().setTitle(title);
    }


    public void setHelpTopic(String topicName)
    {
        setHelpTopic(new HelpTopic(topicName));
    }


    public void setHelpTopic(HelpTopic topic)
    {
        assert null != getPageConfig() : "action not initialized property";
        getPageConfig().setHelpTopic(topic);
    }


    protected Object newInstance(Class c)
    {
        try
        {
            return c == null ? null : c.newInstance();
        }
        catch (Exception x)
        {
            if (x instanceof RuntimeException)
                throw ((RuntimeException)x);
            else
                throw new RuntimeException(x);
        }
    }


    protected FORM getCommand(HttpServletRequest request) throws Exception
    {
        if (getCommandClass() == null)
        {
            return (FORM)new Object();
        }
        FORM command = (FORM) createCommand();

        if (command instanceof HasViewContext)
            ((HasViewContext)command).setViewContext(getViewContext());

        return command;
    }


    protected FORM getCommand() throws Exception
    {
        return getCommand(getViewContext().getRequest());
    }


    //
    // PARAMETER BINDING
    //
    // don't assume parameters always come from a request, use PropertyValues interface
    //

    public BindException defaultBindParameters(FORM form, PropertyValues params)
    {
        return defaultBindParameters(form, getCommandName(), params);
    }


    public static BindException defaultBindParameters(Object form, String commandName, PropertyValues params)
    {
        /* check for do-it-myself forms */
        if (form instanceof HasBindParameters)
        {
            return ((HasBindParameters)form).bindParameters(params);
        }
        
        /* 'regular' commandName handling */
        if (null != params && null != params.getPropertyValue(DataRegion.OLD_VALUES_NAME))
        {
            try
            {
                Object oldObject = PageFlowUtil.decodeObject((String)params.getPropertyValue(DataRegion.OLD_VALUES_NAME).getValue());
                PropertyUtils.copyProperties(form, oldObject);
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }

        if (form instanceof DynaBean)
        {
            return simpleBindParameters(form, commandName, params);
        }
        else
        {
            return springBindParameters(form, commandName, params);
        }
    }


    public static BindException springBindParameters(Object command, String commandName, PropertyValues params)
    {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(command, commandName);
        ConvertHelper.getPropertyEditorRegistrar().registerCustomEditors(binder);
        BindingErrorProcessor defaultBEP = binder.getBindingErrorProcessor();
        binder.setBindingErrorProcessor(getBindingErrorProcessor(defaultBEP));
        binder.setFieldMarkerPrefix(SpringActionController.FIELD_MARKER);
        try
        {
            binder.bind(params);
            BindException errors = new NullSafeBindException(binder.getBindingResult());
            return errors;
        }
        catch (InvalidPropertyException x)
        {
            // Maybe we should propagate exception and return SC_BAD_REQUEST (in ExceptionUtil.handleException())
            // most POST handlers check erros.hasErrors(), but not all GET handlers do
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding property: " + x.getPropertyName());
            return errors;
        }
        catch (NumberFormatException x)
        {
            // Malfomed array parameter throws this exception, unfortunately. Just reject the request. #21931
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding array property; invalid array index (" + x.getMessage() + ")");
            return errors;
        }
    }


    static BindingErrorProcessor getBindingErrorProcessor(final BindingErrorProcessor defaultBEP)
    {
        return new BindingErrorProcessor()
        {
            public void processMissingFieldError(String missingField, BindingResult bindingResult)
            {
                defaultBEP.processMissingFieldError(missingField, bindingResult);
            }

            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult)
            {
                Object newValue = ex.getPropertyChangeEvent().getNewValue();
                if (newValue instanceof String)
                    newValue = StringUtils.trimToNull((String)newValue);

                // convert NULL conversion errors to required errors
                if (null == newValue)
                    defaultBEP.processMissingFieldError(ex.getPropertyChangeEvent().getPropertyName(), bindingResult);
                else
                    defaultBEP.processPropertyAccessException(ex, bindingResult);
            }
        };
    }


    /*
     * This binder doesn't have much to offer over the standard spring data binding except that it will
     * handle DynaBeans.
     */
    public static BindException simpleBindParameters(Object command, String commandName, PropertyValues params)
    {
        //params = _fixupPropertyMap(params);

        BindException errors = new NullSafeBindException(command, "Form");

        // unfortunately ObjectFactory and BeanObjectFactory are not good about reporting errors
        // do this by hand
        for (PropertyValue pv : params.getPropertyValues())
        {
            String propertyName = pv.getName();
            Object value = pv.getValue();
            try
            {
                Object converted = value;
                Class propClass = PropertyUtils.getPropertyType(command, propertyName);
                if (null == propClass)
                    continue;
                if (value == null)
                {
                    /*  */
                }
                else if (propClass.isPrimitive())
                {
                    converted = ConvertUtils.convert(String.valueOf(value), propClass);
                }
                else if (propClass.isArray())
                {
                    if (value instanceof Collection)
                        value = ((Collection) value).toArray(new String[((Collection) value).size()]);
                    else if (!value.getClass().isArray())
                        value = new String[] {String.valueOf(value)};
                    converted = ConvertUtils.convert((String[])value, propClass);
                }
                PropertyUtils.setProperty(command, propertyName, converted);
            }
            catch (ConversionException x)
            {
                errors.addError(new FieldError(commandName, propertyName, value, true, new String[] {"ConversionError", "typeMismatch"}, null, "Could not convert to value: " + String.valueOf(value)));
            }
            catch (Exception x)
            {
                errors.addError(new ObjectError(commandName, new String[]{"Error"}, new Object[] {value}, x.getMessage()));
                Logger.getLogger(BaseViewAction.class).error("unexpected error", x);
            }
        }
        return errors;
    }

    protected Map<String,Object> _fixupPropertyMap(Map<String,Object> in)
    {
        Map<String,Object> out = new HashMap<>(in);

        /** see TableViewForm.setTypedValues() */
        for (Map.Entry<String,Object> entry : in.entrySet())
        {
            String propName = entry.getKey();
            Object o = entry.getValue();

            if (Character.isUpperCase(propName.charAt(0)))
            {
                out.remove(propName);
                propName = Introspector.decapitalize(propName);
                out.put(propName,o);
            }
        }

        out.remove("container");
        out.remove("user");
        return out;
    }

    public boolean supports(Class clazz)
    {
        return getCommandClass().isAssignableFrom(clazz);
    }


    /* for TableViewForm, uses BeanUtils to work with DynaBeans */
    static public class BeanUtilsPropertyBindingResult extends BeanPropertyBindingResult
    {
        public BeanUtilsPropertyBindingResult(Object target, String objectName)
        {
            super(target, objectName);
        }

        protected BeanWrapper createBeanWrapper()
        {
            return new BeanUtilsWrapperImpl((DynaBean)getTarget());
        }
    }

    static public class BeanUtilsWrapperImpl extends AbstractPropertyAccessor implements BeanWrapper
    {
        private Object object;
        private boolean autoGrowNestedPaths = false;
        private int autoGrowCollectionLimit = 0;

        public BeanUtilsWrapperImpl()
        {
            // registerDefaultEditors();
        }
        
        public BeanUtilsWrapperImpl(DynaBean target)
        {
            this();
            object = target;
        }

        public Object getPropertyValue(String propertyName) throws BeansException
        {
            try
            {
                return PropertyUtils.getProperty(object, propertyName);
            }
            catch (Exception e)
            {
                throw new NotReadablePropertyException(object.getClass(), propertyName);
            }
        }

        public void setPropertyValue(String propertyName, Object value) throws BeansException
        {
            try
            {
                PropertyUtils.setProperty(object, propertyName, value);
            }
            catch (Exception e)
            {
                throw new NotWritablePropertyException(object.getClass(), propertyName);
            }
        }

        public boolean isReadableProperty(String propertyName)
        {
            return true;
        }

        public boolean isWritableProperty(String propertyName)
        {
            return true;
        }

        @Override
        public TypeDescriptor getPropertyTypeDescriptor(String s) throws BeansException
        {
            return null;
        }

        public void setWrappedInstance(Object obj)
        {
            object = obj;
        }

        public Object getWrappedInstance()
        {
            return object;
        }

        public Class getWrappedClass()
        {
            return object.getClass();
        }

        public PropertyDescriptor[] getPropertyDescriptors()
        {
            throw new UnsupportedOperationException();
        }

        public PropertyDescriptor getPropertyDescriptor(String propertyName) throws BeansException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAutoGrowNestedPaths(boolean b)
        {
            this.autoGrowNestedPaths = b;
        }

        @Override
        public boolean isAutoGrowNestedPaths()
        {
            return this.autoGrowNestedPaths;
        }

        @Override
        public void setAutoGrowCollectionLimit(int i)
        {
            this.autoGrowCollectionLimit = i;
        }

        @Override
        public int getAutoGrowCollectionLimit()
        {
            return this.autoGrowCollectionLimit;
        }

        public Object convertIfNecessary(Object value, Class requiredType) throws TypeMismatchException
        {
            if (value == null)
                return null;
            return ConvertUtils.convert(String.valueOf(value), requiredType);
        }

        public Object convertIfNecessary(Object value, Class requiredType, MethodParameter methodParam) throws TypeMismatchException
        {
            return convertIfNecessary(value, requiredType);
        }
    }

    public void checkPermissions() throws UnauthorizedException
    {
        checkPermissions(_unauthorizedType);
    }

    protected void checkPermissions(UnauthorizedException.Type unauthorizedType) throws UnauthorizedException
    {
        // ideally, we should pass the bound FORM to getContextualRoles so that
        // actions can determine if the OwnerRole should apply, but this would require
        // a large amount of rework that is too much to consider now.
        //
        // If the action needs Basic Authentication.  Do the standard
        // permissions check, but ignore terms-of-use (non-web clients can't view/respond to terms of use page).  If user
        // is guest and unauthorized, then send a Basic Authentication request.
        try
        {
            checkPermissionsAndTermsOfUse(getClass(), getViewContext(), getContextualRoles());
        }
        catch (TermsOfUseException e)
        {
            if (unauthorizedType == UnauthorizedException.Type.sendBasicAuth || unauthorizedType == UnauthorizedException.Type.sendUnauthorized)
                return;
            throw e;
        }
        catch (UnauthorizedException e)
        {
            e.setType(unauthorizedType);
            throw e;
        }
    }


    /**
     * Actions may provide a set of {@link Role}s used during permission checking
     * or null if no contextual roles apply.
     */
    @Nullable
    protected Set<Role> getContextualRoles()
    {
        return null;
    }


    public static void checkActionPermissions(Class<? extends Controller> actionClass, ViewContext context, Set<Role> contextualRoles) throws UnauthorizedException
    {
        try
        {
            SecurityLogger.indent("BaseViewAction.checkActionPermissions(" + actionClass.getName() + ")");
            _checkActionPermissions(actionClass, context, contextualRoles);
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    private static void _checkActionPermissions(Class<? extends Controller> actionClass, ViewContext context, Set<Role> contextualRoles) throws UnauthorizedException
    {
        String method = context.getRequest().getMethod();
        boolean isPOST = "POST".equals(method);

        Container c = context.getContainer();
        User user = context.getUser();

        if (c.isForbiddenProject(user))
            throw new ForbiddenProjectException();

        boolean requiresSiteAdmin = actionClass.isAnnotationPresent(RequiresSiteAdmin.class);
        if (requiresSiteAdmin && !user.isSiteAdmin())
        {
            throw new UnauthorizedException();
        }

        boolean requiresLogin = actionClass.isAnnotationPresent(RequiresLogin.class);
        if (requiresLogin && user.isGuest())
        {
            throw new UnauthorizedException();
        }

        // User must have ALL permissions in this set
        Set<Class<? extends Permission>> permissionsRequired = new HashSet<>();

        RequiresPermission requiresPerm = actionClass.getAnnotation(RequiresPermission.class);
        if (null != requiresPerm)
        {
            permissionsRequired.add(requiresPerm.value());
        }

        RequiresAllOf requiresAllOf = actionClass.getAnnotation(RequiresAllOf.class);
        if (null != requiresAllOf)
        {
            Collections.addAll(permissionsRequired, requiresAllOf.value());
        }

        RequiresPermissionClass requiresPermClass = actionClass.getAnnotation(RequiresPermissionClass.class);
        if (null != requiresPermClass)
        {
            permissionsRequired.add(requiresPermClass.value());
        }

        // Special handling for admin console actions to support TroubleShooter role. Only site admins can POST,
        // but those with AdminReadPermission (i.e., TroubleShooters) can GET. 
        boolean adminConsoleAction = actionClass.isAnnotationPresent(AdminConsoleAction.class);
        if (adminConsoleAction)
        {
            if (!c.isRoot())
                throw new NotFoundException();

            if (isPOST)
            {
                if (!user.isSiteAdmin())
                {
                    throw new UnauthorizedException();
                }
            }
            else
            {
                permissionsRequired.add(AdminReadPermission.class);
            }
        }

        ContextualRoles rolesAnnotation = actionClass.getAnnotation(ContextualRoles.class);
        if (rolesAnnotation != null)
        {
            contextualRoles = RoleManager.mergeContextualRoles(context, rolesAnnotation.value(), contextualRoles);
        }

        // Policy must have all permissions in permissionsRequired
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
        if (!policy.hasPermissions(user, permissionsRequired, contextualRoles))
            throw new UnauthorizedException();

        boolean requiresAdmin = permissionsRequired.contains(AdminPermission.class);

        if (isPOST)
        {
            boolean csrfCheck = requiresSiteAdmin || requiresAdmin || actionClass.isAnnotationPresent(CSRF.class);
            if (csrfCheck)
                CSRFUtil.validate(context);
        }

        // User must have at least one permission in this set
        Set<Class<? extends Permission>> permissionsAnyOf = Collections.emptySet();
        RequiresAnyOf requiresAnyOf = actionClass.getAnnotation(RequiresAnyOf.class);
        if (null != requiresAnyOf)
        {
            permissionsAnyOf = new HashSet<>();
            Collections.addAll(permissionsAnyOf, requiresAnyOf.value());
            if (!policy.hasOneOf(user, permissionsAnyOf, contextualRoles))
                throw new UnauthorizedException();
        }

        boolean requiresNoPermission = actionClass.isAnnotationPresent(RequiresNoPermission.class);

        if (permissionsRequired.isEmpty() && !requiresSiteAdmin && !requiresLogin && !requiresNoPermission && !adminConsoleAction && permissionsAnyOf.isEmpty())
        {
            throw new ConfigurationException("@RequiresPermission, @RequiresPermission, @RequiresSiteAdmin, @RequiresLogin, @RequiresNoPermission, or @AdminConsoleAction annotation is required on class " + actionClass.getName());
        }

        // All permission checks have succeeded.  Now check for deprecated action.
        if (actionClass.isAnnotationPresent(DeprecatedAction.class))
            throw new DeprecatedActionException(actionClass);
    }


    public static void checkPermissionsAndTermsOfUse(Class<? extends Controller> actionClass, ViewContext context, Set<Role> contextualRoles)
            throws UnauthorizedException
    {
        checkActionPermissions(actionClass, context, contextualRoles);

        boolean requiresTermsOfUse = !actionClass.isAnnotationPresent(IgnoresTermsOfUse.class);
        if (requiresTermsOfUse && !context.hasAgreedToTermsOfUse())
            throw new TermsOfUseException();
    }

    /**
     * @return a map from form element name to uploaded files
     */
    protected Map<String, MultipartFile> getFileMap()
    {
        if (getViewContext().getRequest() instanceof MultipartHttpServletRequest)
            return (Map<String, MultipartFile>)((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
        return Collections.emptyMap();
    }

    protected List<AttachmentFile> getAttachmentFileList()
    {
        return SpringAttachmentFile.createList(getFileMap());
    }

    public boolean isRobot()
    {
        return _robot;
    }

    public boolean isPrint()
    {
        return _print;
    }

    public boolean isDebug()
    {
        return _debug;
    }

    public Class getCommandClass()
    {
        return _commandClass;
    }

    public void setCommandClass(Class commandClass)
    {
        _commandClass = commandClass;
    }

    protected final Object createCommand() throws Exception
    {
        return BeanUtils.instantiateClass(getCommandClass());
    }

    public void setCommandName(String commandName)
    {
        _commandName = commandName;
    }

    public String getCommandName()
    {
        return _commandName;
    }

    /**
     * Set the HTTP methods that this content generator should support.
     * Default is GET, HEAD and POST
     * @param methods
     */
    public void setSupportedMethods(String[] methods)
    {
        _supportedMethods = methods;
    }

    public String[] getSupportedMethods()
    {
        return _supportedMethods;
    }
}
