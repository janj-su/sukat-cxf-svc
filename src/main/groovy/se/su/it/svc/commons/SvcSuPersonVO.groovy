package se.su.it.svc.commons

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlRootElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlElement

@XmlAccessorType( XmlAccessType.NONE )
@XmlRootElement
public class SvcSuPersonVO implements Serializable{
  static final long serialVersionUID = -687991492884235073L
  @XmlAttribute
  String eduPersonPrimaryAffiliation
  @XmlElement(name="eduPersonAffiliation")
  Set<String> eduPersonAffiliation
  @XmlElement(name="eduPersonEntitlement")
  Set<String> eduPersonEntitlement
  @XmlAttribute
  String socialSecurityNumber
  @XmlAttribute
  String givenName
  @XmlAttribute
  String sn
  @XmlAttribute
  String cn
  @XmlAttribute
  String displayName
  @XmlAttribute
  String title
  @XmlAttribute
  String roomNumber
  @XmlAttribute
  String telephoneNumber
  @XmlAttribute
  String mobile
  @XmlElement(name="sukatPULAttributes")
  Set<String> sukatPULAttributes //Adress (hem),Fax (hem),Hemsida (privat/hem),Mail (privat/hem),Mobil,Mobil (privat/hem),Stad (hem),Telefon (privat/hem)
  @XmlAttribute
  String labeledURI //hemsida
  @XmlElement(name="mail")
  Set<String> mail
  @XmlElement(name="mailLocalAddress")
  Set<String> mailLocalAddress
  @XmlAttribute
  String sukatLOAFromDate //Tjänstledighet börjar
  @XmlAttribute
  String sukatLOAToDate   //Tjänstledighet slutar
  @XmlElement(name="eduPersonOrgUnitDN")
  Set<String> eduPersonOrgUnitDN
  @XmlAttribute
  String eduPersonPrimaryOrgUnitDN
  @XmlAttribute
  String registeredAddress
  @XmlAttribute
  String mailRoutingAddress
  @XmlAttribute
  String departmentNumber
  @XmlAttribute
  String homeMobilePhone
  @XmlAttribute
  String homePhone
  @XmlElement(name="homePostalAddress")
  Set<String> homePostalAddress
  @XmlAttribute
  String homeLocalityName
  @XmlAttribute
  String homePostalCode
  @XmlAttribute
  String description
  @XmlAttribute
  String sukatComment
}
