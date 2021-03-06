import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, ManualSubscription, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class PersistanceConsumer {

  val system = ActorSystem()
  val config = ConfigFactory.load()
  implicit val exec: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer.create(system)

  val TOPIC = "all"
  private val BROKER_LIST = config.getString("address")

  val consumerSettings: ConsumerSettings[Array[Byte], String] = ConsumerSettings[Array[Byte], String](system, new ByteArrayDeserializer, new StringDeserializer)
    .withBootstrapServers(BROKER_LIST)
    .withGroupId("test")
    .withPollTimeout(Integer.MAX_VALUE.millisecond)
    .withWakeupTimeout(Integer.MAX_VALUE.millisecond)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val partition = 0

  val subscription: ManualSubscription = Subscriptions.assignment(
    new TopicPartition(TOPIC, partition)
  )

  Consumer.committableSource(consumerSettings, subscription)
    .mapAsync(1) { msg =>
      (for {
        r <- Future.fromTry(SignUpRow.apply(msg.record.value()))
        _ <- DB.create(r)
      } yield msg).recoverWith { case err => println(err.getMessage); Future.successful{msg}}}
    .map(_.committableOffset.commitScaladsl())
    .runWith(Sink.ignore)
}
