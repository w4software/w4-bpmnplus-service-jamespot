package eu.w4.contrib.jamespot;

import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eu.w4.common.configuration.Configuration;
import eu.w4.common.configuration.ConfigurationKeyNotFoundException;
import eu.w4.common.exception.CheckedException;
import eu.w4.common.log.Logger;
import eu.w4.common.log.LoggerFactory;
import eu.w4.engine.client.User;
import eu.w4.engine.client.UserAttachment;
import eu.w4.engine.client.UserIdentifier;
import eu.w4.engine.client.bpmn.w4.runtime.ActivityInstanceAttachment;
import eu.w4.engine.client.bpmn.w4.runtime.DataEntry;
import eu.w4.engine.client.bpmn.w4.runtime.ProcessInstance;
import eu.w4.engine.client.bpmn.w4.runtime.ProcessInstanceAttachment;
import eu.w4.engine.client.bpmn.w4.runtime.ServiceExecutionException;
import eu.w4.engine.client.bpmn.w4.runtime.UserTaskInstance;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.client.service.ObjectFactory;
import eu.w4.engine.core.bpmn.service.AbstractService;
import eu.w4.engine.core.bpmn.service.ActivityInstanceAction;
import eu.w4.engine.core.bpmn.service.ConfigurationFileNames;
import eu.w4.engine.core.bpmn.service.DefaultActivityInstanceResult;
import eu.w4.engine.core.bpmn.service.ExecutionContext;
import eu.w4.engine.core.bpmn.service.Name;
import eu.w4.engine.core.bpmn.service.Result;
import eu.w4.engine.core.bpmn.service.Scope;
import eu.w4.engine.core.bpmn.service.Version;

@Name("jamespot")
@Version("1.0")
@ConfigurationFileNames({ "jamespot" })
public class JamespotService extends AbstractService
{

  private static final String VERSION = "2.0";

  private Logger _logger = LoggerFactory.getLogger(JamespotService.class.getName());
  private Scope _scope;
  private ExecutionContext _executionContext;
  private EngineService _engineService;
  private ObjectFactory _objectFactory;
  private Configuration _configuration;

  private Client _restClient;

  private int _requestValidity;
  private String _url;
  
  private String _module;
  private String _sharedSecret;

  private SimpleDateFormat _iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
  private static final Charset UTF8 = Charset.forName("UTF-8"); 

  @Override
  public void afterInit(Scope scope, ExecutionContext executionContext)
    throws CheckedException, RemoteException
  {
    super.afterInit(scope, executionContext);
    _configuration = getConfiguration();
    _scope = scope;
    _executionContext = executionContext;
    _engineService = _executionContext.getEngineService();
    _objectFactory = _engineService.getObjectFactory();

    _url = _configuration.getValue("jamespot.url");
    _module = _configuration.getValue("security.module");
    _sharedSecret = _configuration.getValue("security.secret");
    _requestValidity = Integer.parseInt(_configuration.getValue("security.ttl"));

    _restClient = ClientBuilder.newClient();
  }

  private void sign(final Map<String, String> message)
  {
    final String timestamp = _iso8601.format(new Date(new Date().getTime() + _requestValidity));

    final byte[] toSign = (message.get("m") + "-" + timestamp + "-" + _sharedSecret).getBytes(UTF8);

    final MessageDigest messageDigest;
    try
    {
      messageDigest = MessageDigest.getInstance("MD5");
    }
    catch (final NoSuchAlgorithmException e)
    {
      throw new RuntimeException("Prerequisite not satisfied: MD5 was not supported by underlying JRE", e);
    }

    byte[] digest = messageDigest.digest(toSign);

    final StringBuffer signature = new StringBuffer(digest.length * 2);
    for (byte b : digest)
    {
      String hex = Integer.toString(b & 0xFF, 16);
      if (hex.length() == 1)
      {
        signature.append("0");
      }
      signature.append(hex);
    }
    message.put("d", timestamp);
    message.put("k", signature.toString().toUpperCase());
  }

  @SuppressWarnings("unchecked")
  private Object call(String user, String object, String function, Map<String, String> parameters) throws ServiceExecutionException
  {
    final Map<String, String> postParameters = new HashMap<String, String>();
    postParameters.putAll(parameters);
    postParameters.put("v", VERSION);
    postParameters.put("m", _module);
    postParameters.put("o", object);
    postParameters.put("f", function);
    if (user.contains("@"))
    {
      postParameters.put("mail", user);
    }
    else
    {
      postParameters.put("idUser", user);
    }
    sign(postParameters);

    final WebTarget webTarget = _restClient.target(_url + "/api/api.php");
    final Entity<?> entity = Entity.entity(postParameters, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    final Response response = webTarget.request().post(entity);
    Map<String, Object> result = response.readEntity(Map.class);
    Map<String, Object> returnCodeMap = (Map<String, Object>) result.get("RC");
    int returnCode = (Integer) returnCodeMap.get("CODE");
    if (returnCode == 0)
    {
      return result.get("VAL");
    }
    else
    {
      final Object errorMessage = returnCodeMap.get("MSG");
      throw new ServiceExecutionException("Jamespot API returned an error [code " + returnCode + "]: " + errorMessage);
    }
  }

  private String getDefault(final String key, final String object) throws CheckedException, RemoteException
  {
    String value = null;
    if (object != null)
    {
      try
      {
        value = _configuration.getValue("default." + object + "." + key);
      }
      catch (ConfigurationKeyNotFoundException e)
      {
        value = null;
      }
    }
    if (value == null)
    {
      try
      {
        value = _configuration.getValue("default." + key);
      }
      catch (ConfigurationKeyNotFoundException e)
      {
        value = null;
      }
    }
    return value;
  }

  private List<String> getDefaults(final String key, final String object) throws CheckedException, RemoteException
  {
    List<String> value = null;
    if (object != null)
    {
      try
      {
        value = _configuration.getValues("default." + object + "." + key);
      }
      catch (ConfigurationKeyNotFoundException e)
      {
        value = null;
      }
    }
    if (value == null)
    {
      try
      {
        value = _configuration.getValues("default." + key);
      }
      catch (ConfigurationKeyNotFoundException e)
      {
        value = null;
      }
    }
    return value;
  }

  private Object get(final String key, final String object) throws CheckedException, RemoteException
  {
    Object value = null;
    switch (_scope)
    {
      case NOTIFICATION:
      case SYNCHRONOUS_SERVICE_CALL:
        value = _executionContext.getAttachedElements().get(key);
        break;
      case SERVICE_TASK:
        DataEntry dataEntry = getActivityInstance().getDataEntries().get(key);
        if (dataEntry != null)
        {
          value = dataEntry.getValue();
        }
        break;
      default:
        value = null;
    }
    if (value == null)
    {
      value = getDefault(key, object);
    }
    return value;
  }

  private final Map<String, String> getDefaultParameters(final String object) throws RemoteException, CheckedException
  {
    final List<String> keys = getDefaults("parameters", object);
    final Map<String, String> defaultParameters = new HashMap<String, String>();
    for (final String key : keys)
    {
      defaultParameters.put(key, getDefault("parameter." + key, object));
    }
    return defaultParameters;
  }

  private final Map<String, String> getRuntimeParameters(final String object) throws RemoteException, CheckedException
  {
    final Map<String, String> parameters = new HashMap<String, String>();
    switch (_scope)
    {
      case NOTIFICATION:
      case SYNCHRONOUS_SERVICE_CALL:
        for (final Entry<String, Object> entry : _executionContext.getAttachedElements().entrySet())
        {
          if (entry.getValue() != null)
          {
            parameters.put(entry.getKey(), entry.getValue().toString());
          }
          else
          {
            parameters.remove(entry.getKey());
          }
        }
        break;
      case SERVICE_TASK:
        for (final DataEntry dataEntry : getActivityInstance().getDataEntries().values())
        {
          if (dataEntry.getValue() != null)
          {
            parameters.put(dataEntry.getName(), dataEntry.getValue().toString());
          }
          else
          {
            parameters.remove(dataEntry.getName());
          }
        }
        break;
      default:
    }
    parameters.remove("object");
    parameters.remove("function");
    parameters.remove("module");
    parameters.remove("version");
    parameters.remove("key");
    parameters.remove("idUser");
    parameters.remove("mail");
    parameters.remove("user");
    parameters.remove("o");
    parameters.remove("f");
    parameters.remove("m");
    parameters.remove("v");
    parameters.remove("k");
    return parameters;
  }

  private User toUserObject(Object any) throws CheckedException, RemoteException
  {
    if (any == null)
    {
      return null;
    }
    else if (any instanceof User)
    {
      return (User) any;
    }
    else if (any instanceof String)
    {
      final UserIdentifier userIdentifier = _objectFactory.newUserIdentifier();
      userIdentifier.setName((String) any);
      return _engineService.getUserService().getUser(_executionContext.getPrincipal(), userIdentifier, getUserAttachement());
    }
    throw new ServiceExecutionException("Class [" + any.getClass() + "] is not supported for user");
  }

  @Override
  public Result execute() throws CheckedException, RemoteException
  {
    final ProcessInstance processInstance = getProcessInstance();

    final String object = (String) get("object", null);
    if (object == null)
    {
      throw new ServiceExecutionException("Could not resolve which API object to target");
    }

    final String function = (String) get("function", object);
    if (function == null)
    {
      throw new ServiceExecutionException("Could not resolve which API function to invoke on [" + object + "]");
    }

    final Object anyUser = get("user", null);
    User user = toUserObject(anyUser);
    if (user == null && _scope == Scope.NOTIFICATION && getActivityInstance() instanceof UserTaskInstance)
    {
      final UserTaskInstance userTaskInstance = (UserTaskInstance) getActivityInstance();
      if (userTaskInstance.getActualOwner() != null)
      {
        user = userTaskInstance.getActualOwner();
      }
      else if (userTaskInstance.getPotentialAndNotExcludedOwnerUsers() != null && userTaskInstance.getPotentialAndNotExcludedOwnerUsers().size() == 1)
      {
        user = userTaskInstance.getPotentialAndNotExcludedOwnerUsers().iterator().next();
      }
    }
    if (user == null)
    {
      user = processInstance.getInitiator();
    }
    final String userMail = (String) user.getProperties().get("email");

    final Map<String, String> effectiveParameters = new HashMap<String, String>(getDefaultParameters(object));
    effectiveParameters.putAll(getRuntimeParameters(object));
    call(userMail, object, function, effectiveParameters);

    switch (_scope)
    {
      case NOTIFICATION:
        return null;
      case SERVICE_TASK:
        return new DefaultActivityInstanceResult(ActivityInstanceAction.COMPLETE);
      default:
        throw new RuntimeException("Scope is unknown");
    }
  }

  @Override
  public ProcessInstanceAttachment getProcessInstanceAttachment() throws CheckedException, RemoteException
  {
    final ProcessInstanceAttachment processInstanceAttachment = _objectFactory.newProcessInstanceAttachment();
    processInstanceAttachment.setInitiatorAttached(true);
    processInstanceAttachment.setInitiatorAttachment(getUserAttachement());
    return processInstanceAttachment;
  }

  @Override
  public ActivityInstanceAttachment getActivityInstanceAttachment() throws CheckedException, RemoteException
  {
    final ActivityInstanceAttachment activityInstanceAttachment = _objectFactory.newActivityInstanceAttachment();
    activityInstanceAttachment.setDataEntriesAttached(true);
    activityInstanceAttachment.setUsersAttachment(getUserAttachement());
    return activityInstanceAttachment;
  }

  public UserAttachment getUserAttachement() throws CheckedException, RemoteException
  {
    final UserAttachment userAttachment = _objectFactory.newUserAttachment();
    return userAttachment;
  }
}
