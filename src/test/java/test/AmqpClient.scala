package test

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import com.rabbitmq.client.QueueingConsumer

import edu.umro.dicom.service.ServiceConfig


class Recv extends Runnable {
    override def run = {
            println("starting receiver ...")
            val queueName = "Aria.Event.EventDicomObject";
            val factory = new ConnectionFactory
            factory.setHost(ServiceConfig.getInstance.getAmqpBrokerHost)
            val connection = factory.newConnection
            val channel = connection.createChannel

            channel.queueDeclare(queueName, false, false, false, null);
            println(" [*] Waiting for messages. To exit press CTRL+C");

            val consumer = new QueueingConsumer(channel);
            channel.basicConsume(queueName, true, consumer);

            while (true) {
                val delivery = consumer.nextDelivery
                val message = new String(delivery.getBody)
                System.out.println(" [x] Received '" + message + "'");
            }
    }

    (new Thread(this)).start
    Thread.sleep(500) // wait to make sure that the receiver has started
}


object AmqpClient {


    def main(args: Array[String]): Unit = {
            try {
                val queueName = "Aria.Event.EventDicomObject"
                println("queueName: " + queueName)

                val factory = new ConnectionFactory

                factory.setHost(ServiceConfig.getInstance.getAmqpBrokerHost)

                new Recv
                
                while (true) {
                    //println("Sleeping to keep a connection to the queue ...")
                    Thread.sleep(60 * 1000)
                }

                val connection = factory.newConnection
                val channel = connection.createChannel

                try {
                    val ok = channel.queueDeclarePassive(queueName)

                    println("ok: " + ok)
                }
                catch {
                    case e:Exception => {
                        println("\nBadness !!!! e: " + e)
                        e.printStackTrace
                    }
                }
                //System.exit(99)

                //channel.queueDeclare(queueName, false, false, false, null)
                val message = "Hello There!";
                val exchange = ServiceConfig.getInstance.getMRCT.exchange
                channel.basicPublish(exchange, queueName, null, message.getBytes())
                println(" [x] Sent '" + message + "'")

                channel.close()
                connection.close()

            }
            catch {
                case e:Exception => {
                    println(e)
                    e.printStackTrace
                }
            }
            System.exit(0)
    }


}