/*
 * Copyright (c) 2013, IT Services, Stockholm University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Stockholm University nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package se.su.it.svc

import groovy.util.logging.Slf4j
import org.apache.commons.lang.NotImplementedException
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires

import se.su.it.svc.commons.LdapAttributeValidator
import se.su.it.svc.commons.SvcPostalAddressVO
import se.su.it.svc.commons.SvcSuPersonVO
import se.su.it.svc.commons.SvcSubAccountVO
import se.su.it.svc.commons.SvcUidPwd
import se.su.it.svc.ldap.SuPerson
import se.su.it.svc.ldap.SuPersonStub
import se.su.it.svc.manager.ConfigManager

import se.su.it.svc.query.AccountQuery
import se.su.it.svc.query.GroupOfUniqueNamesQuery
import se.su.it.svc.query.NamedObjectQuery
import se.su.it.svc.query.SuPersonQuery
import se.su.it.svc.query.UidNumberQuery

import se.su.it.svc.server.annotations.AuditHideReturnValue
import se.su.it.svc.server.annotations.AuthzRole
import se.su.it.svc.util.AccountServiceUtils
import se.su.it.svc.util.GeneralUtils

import javax.annotation.Resource
import javax.jws.WebParam
import javax.jws.WebService
import javax.xml.ws.WebServiceContext

/**
 * Implementing class for AccountService CXF Web Service.
 * This Class handles all Account activities in SUKAT.
 */

@WebService @Slf4j
@AuthzRole(role = "sukat-account-admin")
public class AccountServiceImpl implements AccountService
{

    @Resource
    public WebServiceContext context;

    def configManager
    def uidNumberQuery

    /**
     * Activate a person.
     *
     * @param uid Username
     *
     * @return Password for the activated user
     */
    @Requires({
        ! LdapAttributeValidator.validateAttributes([
            uid: uid
        ])
    })
    @Ensures({ result && result.length() > 10 })
    public String activatePerson(
            @WebParam(name = 'uid') String uid
        )
    {
        throw new RuntimeException("This method is deprecated")
    }

    /**
     * Accepts an array of mailLocalAddresses, the new entries gets compared with the SuPersons
     * current mailLocalAddress entries and new entries gets added to the suPerson entry.
     *
     * @param uid uid for the user
     * @param mailLocalAddresses E-mail addresses to add
     *
     * @return new list of mailLocalAddresses bound to the suPerson after the update.
     */
    @Requires({
        ! LdapAttributeValidator.validateAttributes([
            uid: uid,
            mailLocalAddresses: mailLocalAddresses
        ]) &&
        mailLocalAddresses.size() > 0
    })
    public String[] addMailLocalAddresses(
        @WebParam(name = "uid") String uid,
        @WebParam(name = "mailLocalAddresses") String[] mailLocalAddresses)
    {
        def directory = ConfigManager.LDAP_RW
        def person = SuPersonQuery.getSuPersonFromUID(directory, uid)

        for (mla in mailLocalAddresses)
        {
            mla = mla.toLowerCase()

            def p = SuPersonQuery.findByMailLocalAddress(directory, mla)

            if (p)
            {
                if (p.uid == person.uid)
                {
                    log.info("${uid} already have ${mla} as mailLocalAddress")
                    continue
                }
                else
                {
                    throw new IllegalArgumentException("${mla} is already in use on ${p.dn}")
                }
            }

            def gou = GroupOfUniqueNamesQuery.findByMailLocalAddress(directory, mla)
            if (gou)
            {
                throw new IllegalArgumentException("${mla} is already in use on ${gou.dn}")
            }

            def no = NamedObjectQuery.findByMailLocalAddress(directory, mla)
            if (no)
            {
                throw new IllegalArgumentException("${mla} is already in use on ${no.dn}")
            }

            log.info("Adding ${mla} as new mailLocalAddress for ${uid}")
            person.mailLocalAddress.add(mla)
        }

        SuPersonQuery.updateSuPerson(person)

        // Add paranoia by refreshing the information from the datastore.
        SuPerson rp = SuPersonQuery.getSuPersonFromUID(directory, uid)

        return rp.mailLocalAddress
    }

    /**
     * Create a person (stub).
     *
     * @param nin Twelve digit personnummer
     * @param givenName Given name
     * @param sn Surname
     *
     * @return Username of created user
     */
    @Requires({
        ! LdapAttributeValidator.validateAttributes([
            nin: nin,
            givenName: givenName,
            sn: sn
        ])
    })
    @Ensures({ result && result.length() == 8 })
    public String createPerson(
            @WebParam(name = 'nin') String nin,
            @WebParam(name = 'givenName') String givenName,
            @WebParam(name = 'sn') String sn
        )
    {
        throw new RuntimeException("This method is deprecated")
    }

    /**
     * Set homePostalAddress and related attributes.
     *
     * @param uid uid of the user.
     * @param address Address to be set.
     */
    @Requires({
        address &&
        ! LdapAttributeValidator.validateAttributes([
            uid: uid
        ])
    })
    public void setHomePostalAddress(
            @WebParam(name = 'uid') String uid,
            @WebParam(name = 'address') SvcPostalAddressVO address
        )
    {
        throw new RuntimeException("This method is deprecated")
    }

    /**
     * Set titles, swedish is required, english is optional.
     *
     * @param sv Swedish title
     * @param en English title
     */
    @Requires({
        sv &&
        en != null &&
        ! LdapAttributeValidator.validateAttributes([
            uid: uid
        ])
    })
    public void setTitle(
            @WebParam(name = 'uid') String uid,
            @WebParam(name = 'sv') String sv,
            @WebParam(name = 'en') String en
        )
    {
        SuPerson person = SuPersonQuery.getSuPersonFromUID(ConfigManager.LDAP_RW, uid)

        person.title = sv

        if (en.length() > 0)
        {
            person.title_en = en
        }
        else
        {
            person.title_en = null
        }

        SuPersonQuery.updateSuPerson(person)
    }

    /**
     * Create sub account for the given uid and type.
     *
     * @param uid uid of the user.
     * @param type Sub account type.
     *
     * @return Value object with uid and password.
     */
    @Requires({
        type &&
        ! LdapAttributeValidator.validateAttributes([
            uid: uid
        ])
    })
    @Ensures({ result && result.uid && result.password })
    public SvcUidPwd createSubAccount(
        @WebParam(name = 'uid') String uid,
        @WebParam(name = 'type') String type
    )
    {
        throw new RuntimeException("This method is deprecated")
    }

  /**
   * Delete sub account for the given uid and type.
   *
   * @param uid uid of the user.
   * @param type Sub account type.
   */
  @Requires({
    type &&
    ! LdapAttributeValidator.validateAttributes([
        uid: uid
    ])
  })
  public void deleteSubAccount(
        @WebParam(name = 'uid') String uid,
        @WebParam(name = 'type') String type
    )
  {
        throw new RuntimeException("This method is deprecated")
  }

  /**
   * Retrieve sub account for the given uid and type.
   *
   * @param uid uid of the user.
   * @param type Sub account type.
   *
   * @return A SvcSubAccountVO.
   */
  @Requires({
    type &&
    ! LdapAttributeValidator.validateAttributes([
        uid: uid
    ])
  })
  @Ensures({ result instanceof SvcSubAccountVO })
  public SvcSubAccountVO getSubAccount(
        @WebParam(name = 'uid') String uid,
        @WebParam(name = 'type') String type
    )
  {
        throw new RuntimeException("This method is deprecated")
  }

  /**
   * This method sets the primary affiliation for the specified uid.
   *
   * @param uid  uid of the user.
   * @param affiliation the affiliation for this uid
   * @throws IllegalArgumentException if the uid can't be found
   * @see se.su.it.svc.ldap.SuPerson
   */
  @Requires({
    ! LdapAttributeValidator.validateAttributes([
            uid: uid,
            eduPersonPrimaryAffiliation: affiliation ])
  })
  public void updatePrimaryAffiliation(
          @WebParam(name = 'uid') String uid,
          @WebParam(name = 'affiliation') String affiliation
  ) {
    SuPerson person = SuPersonQuery.getSuPersonFromUID(ConfigManager.LDAP_RW, uid)

    if(!person) {
      throw new IllegalArgumentException("updatePrimaryAffiliation no such uid found: "+uid)
    }

    person.eduPersonPrimaryAffiliation = affiliation

    log.debug("updatePrimaryAffiliation - Replacing affiliation=<${person?.eduPersonPrimaryAffiliation}> with affiliation=<${affiliation}> for uid=<${uid}>")
    SuPersonQuery.updateSuPerson(person)
    log.info("updatePrimaryAffiliation - Updated affiliation for uid=<${uid}> with affiliation=<${person.eduPersonPrimaryAffiliation}>")
  }

    /**
     * This method resets the password for the specified uid and returns the clear text password.
     *
     * @param uid  uid of the user.
     * @return String new password.
     * @throws IllegalArgumentException if the uid can't be found
     */
    @Requires({
        uid
    })
    @Ensures({ result && result instanceof String && result.size() == 11 })
    @AuditHideReturnValue
    public String resetPassword(
        @WebParam(name = 'uid') String uid
    )
    {
        throw new RuntimeException("This method is deprecated")
    }

    /**
     * This method resets the password for the specified uid, updates assurance and returns the
     * clear text password.
     *
     * @param uid  uid of the user.
     * @param assurance Assurance strings to set
     *
     * @return String new password.
     */
    @Requires({
        uid
    })
    @Ensures({ result && result instanceof String && result.size() == 11 })
    @AuditHideReturnValue
    public String resetPasswordWithAssurance(
        @WebParam(name = 'uid') String uid,
        @WebParam(name = 'assurance') String[] assurance
    )
    {
        throw new RuntimeException("This method is deprecated")
    }

  /**
   * This method resets the password for the specified uid without returning the result.
   *
   * @param uid  uid of the user.
   * @throws IllegalArgumentException if the uid can't be found
   */
  @Requires({
    uid
  })
  public void scramblePassword(
          @WebParam(name = 'uid') String uid
  ) {
        throw new RuntimeException("This method is deprecated")
  }

  /**
   * This method updates the attributes for the specified uid.
   *
   * @param uid  uid of the user.
   * @param person pre-populated SvcSuPersonVO object, the attributes that differ in this object to the original will be updated in ldap.
   * @throws IllegalArgumentException if the uid can't be found
   * @see se.su.it.svc.ldap.SuPerson
   * @see se.su.it.svc.commons.SvcSuPersonVO
   */
  @Requires({
    !LdapAttributeValidator.validateAttributes([
            uid: uid,
            svcsuperson: person ])
  })
  public void updateSuPerson(
          @WebParam(name = 'uid') String uid,
          @WebParam(name = 'person') SvcSuPersonVO person
  ){
    SuPerson originalPerson = SuPersonQuery.getSuPersonFromUID(ConfigManager.LDAP_RW, uid)

    originalPerson.updateFromSvcSuPersonVO(person)
    log.debug("updateSuPerson - Trying to update SuPerson uid<${originalPerson.uid}>")

    SuPersonQuery.updateSuPerson(originalPerson)
    log.info("updateSuPerson - Updated SuPerson uid<${originalPerson.uid}>")
  }

  /**
   * This method terminates the account for the specified uid in sukat.
   *
   * @param uid  uid of the user.
   * @throws NotImplementedException
   */
  @Requires({
    ! LdapAttributeValidator.validateAttributes([
            uid: uid ])
  })
  public void terminateSuPerson(
          @WebParam(name = 'uid') String uid) {
    throw new NotImplementedException()
  }

  /**
   * This method gets the mailroutingaddress for the specified uid in sukat.
   *
   * @param uid  uid of the user.
   * @return the users mailRoutingAddress
   * @throws IllegalArgumentException if the uid can't be found
   */
  @AuthzRole(role = "sukat-account-admin")
  @Requires({
    ! LdapAttributeValidator.validateAttributes([
            uid: uid ])
  })
  @Ensures({ result == null || result instanceof String})
  public String getMailRoutingAddress(
          @WebParam(name = 'uid') String uid
  ) {

    SuPerson suPerson = SuPersonQuery.getSuPersonFromUID(ConfigManager.LDAP_RW, uid)

    return suPerson.mailRoutingAddress
  }

  /**
   * This method sets the mailroutingaddress for the specified uid in sukat.
   *
   * @param uid  uid of the user. 10 chars (YYMMDDXXXX)
   * @param mail mailaddress to be set for uid.
   * @throws IllegalArgumentException if the uid can't be found
   */
  @Requires({
    ! LdapAttributeValidator.validateAttributes([
            uid: uid,
            mailroutingaddress: mailRoutingAddress ])
  })
  public void setMailRoutingAddress(
          @WebParam(name = 'uid') String uid,
          @WebParam(name = 'mailRoutingAddress') String mailRoutingAddress
  ) {
    SuPerson suPerson = SuPersonQuery.getSuPersonFromUID(ConfigManager.LDAP_RW, uid)

    suPerson.mailRoutingAddress = mailRoutingAddress
    SuPersonQuery.updateSuPerson(suPerson)
    log.debug("setMailRoutingAddress - Changed mailroutingaddress to <${mailRoutingAddress}> for uid <${uid}>")
  }

  /**
   * Finds all SuPersons in ldap based on socialSecurityNumber
   *
   * @param ssn in 10 numbers (YYMMDDXXXX)
   * @return an array of SvcSuPersonVO, one for each account found or an empty list
   */
  @Requires({
    ! LdapAttributeValidator.validateAttributes([
            ssn: ssn ])
  })
  @Ensures({ result != null && result instanceof SvcSuPersonVO[] })
  public SvcSuPersonVO[] findAllSuPersonsBySocialSecurityNumber(
          @WebParam(name = "socialSecurityNumber") String ssn
  ) {
    SuPerson[] suPersons = SuPersonQuery.getSuPersonFromSsn(ConfigManager.LDAP_RW, ssn)

    return suPersons ? suPersons*.createSvcSuPersonVO() : []
  }

  /**
   * Finds a SuPerson in ldap based on uid
   *
   * @param uid without (@domain)
   * @return SvcSuPersonVO instance if found otherwise returns null.
   */
  @Requires({
    ! LdapAttributeValidator.validateAttributes([
            uid: uid ])
  })
  @Ensures({ result == null || result instanceof SvcSuPersonVO })
  public SvcSuPersonVO findSuPersonByUid(
          @WebParam(name = 'uid') String uid
  ) {
    SuPerson suPerson = SuPersonQuery.findSuPersonByUID(ConfigManager.LDAP_RW, uid)

    return suPerson ? suPerson?.createSvcSuPersonVO() : null
  }
}
