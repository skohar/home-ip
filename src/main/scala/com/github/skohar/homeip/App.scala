package com.github.skohar.homeip

import java.net.InetAddress

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.route53.AmazonRoute53Client
import com.amazonaws.services.route53.model._
import com.amazonaws.services.s3.AmazonS3Client
import play.api.libs.json.Json

import scala.collection.JavaConversions._
import scala.util.Try

class App {

  def handler(ipAddress: String, context: Context): String = {
    (for {
      config <- loadConfig
      address <- Try(InetAddress.getByName(ipAddress)).toOption
    } yield {
      val recordSet = new AmazonRoute53Client()
          .listResourceRecordSets(new ListResourceRecordSetsRequest(config.hostedZoneId)).getResourceRecordSets
          .filter(_.getType == RRType.A.toString).find(_.getName == s"${config.hostName}.")
      val action = recordSet match {
        case Some(_) => ChangeAction.UPSERT
        case None => ChangeAction.CREATE
      }
      val requestRecordSet = new ResourceRecordSet().withName(config.hostName).withType(RRType.A).withTTL(config.ttl)
        .withResourceRecords(new ResourceRecord(address.getHostAddress) :: Nil)
      val change = new Change(action, requestRecordSet)
      val batch = new ChangeBatch(change :: Nil)
      val request = new ChangeResourceRecordSetsRequest(config.hostedZoneId, batch)
      new AmazonRoute53Client().changeResourceRecordSets(request)
    }) match {
      case Some(_) => "OK"
      case None => ""
    }
  }

  def loadConfig: Option[Config] =
    Json.parse(new AmazonS3Client().getObject("homeip1", "env.json").getObjectContent).asOpt[Config]
}
