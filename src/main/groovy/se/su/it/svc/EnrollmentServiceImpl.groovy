package se.su.it.svc

import org.apache.commons.collections.Predicate
import org.apache.log4j.Logger
import se.su.it.commons.PasswordUtils
import se.su.it.commons.PrincipalUtils
import se.su.it.svc.commons.LdapAttributeValidator
import se.su.it.svc.commons.SvcAudit
import se.su.it.svc.commons.SvcUidPwd
import se.su.it.svc.ldap.SuEnrollPerson
import se.su.it.svc.util.AccountServiceUtils
import se.su.it.svc.util.EnrollmentServiceUtils

import javax.jws.WebService
import javax.jws.WebParam
import se.su.it.svc.ldap.SuPerson
import se.su.it.svc.query.SuPersonQuery
import se.su.it.svc.manager.GldapoManager
import se.su.it.commons.Kadmin

/**
 * Implementing class for EnrollmentService CXF Web Service.
 * This Class handles all enrollment activities in SUKAT/KDC.
 */

@WebService
class EnrollmentServiceImpl implements EnrollmentService{
  private static final Logger logger = Logger.getLogger(EnrollmentServiceImpl.class)

  /**
   * This method creates a new password and retire it for the specified uid.
   *
   *
   * @param uid  uid of the user.
   * @param audit Audit object initilized with audit data about the client and user.
   * @return String with temporary password.
   * @see se.su.it.svc.commons.SvcAudit
   */
  public String resetAndExpirePwd(@WebParam(name = "uid") String uid, @WebParam(name = "audit") SvcAudit audit) {
    if(uid == null || audit == null)
      throw new IllegalArgumentException("resetAndExpirePwd - Null argument values not allowed in this function")
    SuPerson person = SuPersonQuery.getSuPersonFromUID(GldapoManager.LDAP_RW, uid)
    if(person) {
      String pwd = Kadmin.newInstance().resetOrCreatePrincipal(uid.replaceFirst("\\.", "/"))
      Kadmin.newInstance().setPasswordExpiry(uid.replaceFirst("\\.", "/"), new Date())
      return pwd
    } else {
      throw new IllegalArgumentException("resetAndExpirePwd - no such uid found: "+uid)
    }
  }

  /**
   * This method enrolls a user in sukat, kerberos and afs. If not found in sukat user will be created.
   *
   *
   * @param domain      domain of user in sukat. This is used to set the DN if user will be created.
   * @param givenName   givenName of the user. This is used to set the givenName (förnamn) if user will be created.
   * @param sn          sn of the user. This is used to set the sn (efternamn) if user will be created.
   * @param sn          affiliation of the user. This is used to set the affiliation if user will be created.
   * @param nin         nin of the person. This can be a 10 or 12 digit social security number.
   * @param audit       Audit object initilized with audit data about the client and user.
   * @return SvcUidPwd  object with the uid and password.
   * @see se.su.it.svc.commons.SvcAudit
   */
  public SvcUidPwd enrollUser(@WebParam(name = "domain") String domain, @WebParam(name = "givenName") String givenName, @WebParam(name = "sn") String sn, @WebParam(name = "eduPersonPrimaryAffiliation") String eduPersonPrimaryAffiliation, @WebParam(name = "nin") String nin, @WebParam(name = "audit") SvcAudit audit) {

    /** Config value set in config.properties to allow for mocking out user creation */
    boolean skipCreate = se.su.it.svc.manager.Properties.instance.props.getProperty('enrollUser.skipCreate')

    String attributeError = LdapAttributeValidator.validateAttributes(["ssnornin": nin, "domain": domain, "givenName": givenName, "sn": sn, "eduPersonPrimaryAffiliation": eduPersonPrimaryAffiliation, "audit": audit])
    if (attributeError) {
      throw new IllegalArgumentException("enrollUser - ${attributeError}")
    }

    SvcUidPwd svcUidPwd = new SvcUidPwd()
    svcUidPwd.password = PasswordUtils.genRandomPassword(10, 10)

    SuEnrollPerson suEnrollPerson = findEnrollPerson(nin)

    if (suEnrollPerson) {
      handleExistingUser(nin, suEnrollPerson, skipCreate, svcUidPwd, eduPersonPrimaryAffiliation, domain)
    } else {
      /** User not found in SUKAT, create user now */
      handleNewUser(nin, givenName, sn, svcUidPwd, eduPersonPrimaryAffiliation, domain, skipCreate)
    }

    return svcUidPwd
  }

  private static void handleNewUser(String nin, String givenName, String sn, SvcUidPwd svcUidPwd, String eduPersonPrimaryAffiliation, String domain, boolean skipCreate) {
    logger.debug("enrollUser - User with nin <${nin}> not found. Trying to create and enable user in sukat/afs/kerberos.")

    svcUidPwd.uid = generateUid(givenName, sn)

    SuEnrollPerson suCreateEnrollPerson = setupEnrollPerson(svcUidPwd, givenName, sn, eduPersonPrimaryAffiliation, domain, nin)
    SuPersonQuery.initSuEnrollPerson(GldapoManager.LDAP_RW, suCreateEnrollPerson)

    if (skipCreate) {
      logger.warn("createSuPerson - FullAccount attribute not set. PosixAccount entries will not be set and no AFS or KDC entries will be generated.")
      logger.warn("createSuPerson - Password returned will be fake/dummy")
    } else {
      if (EnrollmentServiceUtils.enableUser(suCreateEnrollPerson.uid, svcUidPwd.password, suCreateEnrollPerson)) {
        logger.info("enrollUser - User with uid <${suCreateEnrollPerson.uid}> now enabled.")
      } else {
        logger.error("enrollUser - enroll failed while excecuting perl scripts for uid <${suCreateEnrollPerson.uid}>")
        throw new Exception("enrollUser - enroll failed in scripts.")
      }
    }
  }

  private static SuEnrollPerson setupEnrollPerson(SvcUidPwd svcUidPwd, String givenName, String sn, String eduPersonPrimaryAffiliation, String domain, String nin) {
    SuEnrollPerson suCreateEnrollPerson = new SuEnrollPerson()
    suCreateEnrollPerson.uid = svcUidPwd.uid
    suCreateEnrollPerson.cn = givenName + " " + sn
    suCreateEnrollPerson.sn = sn
    suCreateEnrollPerson.givenName = givenName
    suCreateEnrollPerson.displayName = givenName + " " + sn
    suCreateEnrollPerson.eduPersonPrimaryAffiliation = eduPersonPrimaryAffiliation
    suCreateEnrollPerson.eduPersonAffiliation = [eduPersonPrimaryAffiliation]
    suCreateEnrollPerson.mail = [svcUidPwd.uid + "@" + domain]
    suCreateEnrollPerson.mailLocalAddress = [svcUidPwd.uid + "@" + domain]

    if (nin.length() == 12) {
      suCreateEnrollPerson.norEduPersonNIN = nin
      suCreateEnrollPerson.socialSecurityNumber = nin.substring(2, 12)
    } else {
      suCreateEnrollPerson.socialSecurityNumber = nin
    }

    suCreateEnrollPerson.eduPersonPrincipalName = svcUidPwd.uid + "@su.se"
    suCreateEnrollPerson.objectClass = ["suPerson", "sSNObject", "norEduPerson", "eduPerson", "inetLocalMailRecipient", "inetOrgPerson", "organizationalPerson", "person", "top"]
    suCreateEnrollPerson.parent = AccountServiceUtils.domainToDN(domain)
    logger.debug("createSuPerson - Writing initial sukat record to sukat for uid<${svcUidPwd.uid}>")
    return suCreateEnrollPerson
  }

  private static String generateUid(String givenName, String sn) {
    String uid = PrincipalUtils.suniqueUID(givenName, sn, new Predicate() {
      public boolean evaluate(Object object) {
        try {
          return SuPersonQuery.getSuPersonFromUID(GldapoManager.LDAP_RW, (String) object) == null;
        } catch (ex) {
          logger.error "Failed when getting SuPerson from GID", ex
          return false;
        }
      }
    })

    logger.debug "Returning $uid for user with name $givenName $sn"
    return uid
  }

  private static void handleExistingUser(String nin, SuEnrollPerson suEnrollPerson, boolean skipCreate, SvcUidPwd svcUidPwd, String eduPersonPrimaryAffiliation, String domain) {
    logger.debug("enrollUser - User with nin <${nin}> found. Now enabling uid <${suEnrollPerson.uid}>.")

    boolean enabledUser = enableUser(skipCreate, suEnrollPerson, svcUidPwd)

    if (!enabledUser) {
      logger.error("enrollUser - enroll failed while excecuting perl scripts for uid <${suEnrollPerson.uid}>")
      throw new Exception("enrollUser - enroll failed in scripts.")
    }

    setNin(nin, suEnrollPerson)
    setPrimaryAffiliation(eduPersonPrimaryAffiliation, suEnrollPerson)
    setMailAttributes(suEnrollPerson, domain)
    SuPersonQuery.saveSuEnrollPerson(suEnrollPerson)
    svcUidPwd.uid = suEnrollPerson.uid
    logger.info("enrollUser - User with uid <${suEnrollPerson.uid}> now enabled.")
  }

  private static void setMailAttributes(SuEnrollPerson suEnrollPerson, String domain) {
    String myMail = suEnrollPerson.uid + "@" + domain

    if (suEnrollPerson.mail != null) {
      if (!suEnrollPerson.mail.contains(myMail)) {
        suEnrollPerson.mail.add(myMail)
      }
    } else {
      suEnrollPerson.mail = [myMail]
    }
    if (suEnrollPerson.mailLocalAddress != null) {
      if (!suEnrollPerson.mailLocalAddress.contains(myMail)) {
        suEnrollPerson.mailLocalAddress.add(myMail)
      }
    } else {
      suEnrollPerson.mailLocalAddress = [myMail]
    }
  }

  private static void setPrimaryAffiliation(String eduPersonPrimaryAffiliation, SuEnrollPerson suEnrollPerson) {
    suEnrollPerson.eduPersonPrimaryAffiliation = eduPersonPrimaryAffiliation

    if (suEnrollPerson.eduPersonAffiliation != null) {
      if (!suEnrollPerson.eduPersonAffiliation.contains(eduPersonPrimaryAffiliation)) {
        suEnrollPerson.eduPersonAffiliation.add(eduPersonPrimaryAffiliation)
      }
    } else {
      suEnrollPerson.eduPersonAffiliation = [eduPersonPrimaryAffiliation]
    }
  }

  private static void setNin(String nin, SuEnrollPerson suEnrollPerson) {
    if (nin.length() == 12) {
      suEnrollPerson.norEduPersonNIN = nin
      suEnrollPerson.socialSecurityNumber = nin.substring(2, 12)
    } else {
      suEnrollPerson.socialSecurityNumber = nin
    }
  }

  private static boolean enableUser(boolean skipCreate, SuEnrollPerson suEnrollPerson, SvcUidPwd svcUidPwd) {
    boolean enabledUser
    if (skipCreate) {
      enabledUser = true
      logger.warn "Running in test mode, skipping enable step (perl free)"
    } else {
      enabledUser = EnrollmentServiceUtils.enableUser(suEnrollPerson.uid, svcUidPwd.password, suEnrollPerson)
    }
    return enabledUser
  }

  private static SuEnrollPerson findEnrollPerson(String nin) {
    SuEnrollPerson suEnrollPerson
    if (nin.length() == 10) {
      suEnrollPerson = SuPersonQuery.getSuEnrollPersonFromSsn(GldapoManager.LDAP_RW, nin)
    } else {
      suEnrollPerson = SuPersonQuery.getSuEnrollPersonFromNin(GldapoManager.LDAP_RW, nin)
      if (suEnrollPerson == null) { // Try to cut the 12 - digit ssn to 10
        suEnrollPerson = SuPersonQuery.getSuEnrollPersonFromSsn(GldapoManager.LDAP_RW, nin.substring(2, 12))
      }
    }
    return suEnrollPerson
  }
}
