import groovyx.gpars.actor.AbstractPooledActor

/**
 * Uses Groovy's actors framework (GPars) to send xml requests and reply to the actor
 * that invoked this sender with the connection object (that can later be parsed
 * for the response albeit in the invoker's separate actor).
 */
class RequestSender extends AbstractPooledActor {
  def numAddressesToTest
  //Actors guarantee that always at most one thread processes the actor's body
  // at a time and also under the covers the memory gets synchronized each time a
  // thread gets assigned to an actor so the actor's state can be safely modified
  // by code in the body without any other extra (synchronization or locking) effort .
  def counter = 0 

  public RequestSender (numAddressesToTest) {
    this.numAddressesToTest = numAddressesToTest
  }

  @Override protected void act() {
    loop {
      react {address ->
          reply sendMsg(address)
          counter++
        if (counter == numAddressesToTest) {
          stop()
          println "Stopping Sender"
        }
      }
    }
  }

  def sendMsg(address) {
    println "sending xml request"
    def soapRequest = """<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xsi="http://www.w3.org/1999/XMLSchema-instance"
        xmlns:xsd="http://www.w3.org/1999/XMLSchema"
        xmlns:tns="http://rpc.geocoder.us/Geo/Coder/US/">
        <SOAP-ENV:Body>
          <tns:geocode
              SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <location xsi:type="xsd:string">${address}</location>
          </tns:geocode>
        </SOAP-ENV:Body>
      </SOAP-ENV:Envelope>"""

    def soapUrl = new URL("http://geocoder.us/service/soap")
    def connection = soapUrl.openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/xml")
    connection.doOutput = true
    Writer writer = new OutputStreamWriter(connection.outputStream)
    writer.write(soapRequest)
    writer.flush()
    writer.close()
    connection.connect()

    return connection
  }
}

/**
 * Uses Groovy's actors framework (GPars) to receive xml responses from a separate actor
 */
class ResponseCatcher extends AbstractPooledActor {
  AbstractPooledActor sender
  def addresses = []
  def numAddressesToTest
  //Actors guarantee that always at most one thread processes the actor's body
  // at a time and also under the covers the memory gets synchronized each time a
  // thread gets assigned to an actor so the actor's state can be safely modified
  // by code in the body without any other extra (synchronization or locking) effort .
  def counter = 0

  public ResponseCatcher(sender, numAddressesToTest) {
    this.sender = sender
    this.numAddressesToTest = numAddressesToTest
  }

  @Override protected void act() {

    (1..numAddressesToTest).each {addresses += it + 'Hosmer St, Marlborough, MA 01752'}
    addresses.each {sender.send(it)}
    loop {
      react {connection ->
        println "receiving xml response"
        def soapResponse = connection.content.text

        println soapResponse

        /*
        soapResponse = """<?xml version="1.0" encoding="utf-8"?>
        <SOAP-ENV:Envelope xmlns:xsi="http://www.w3.org/1999/XMLSchema-instance"
        xmlns:SOAP-ENC="http://schemas.xmlsoap.org/soap/encoding/"
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xsd="http://www.w3.org/1999/XMLSchema"
        SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <SOAP-ENV:Body>
            <namesp9:geocodeResponse xmlns:namesp9="http://rpc.geocoder.us/Geo/Coder/US/">

              <geo:s-gensym111 xsi:type="SOAP-ENC:Array"
              xmlns:geo="http://rpc.geocoder.us/Geo/Coder/US/"
              SOAP-ENC:arrayType="geo:GeocoderAddressResult[1]">
                <item xsi:type="geo:GeocoderAddressResult">
                  <number xsi:type="xsd:int">1600</number>
                  <lat xsi:type="xsd:float">38.898748</lat>
                  <street xsi:type="xsd:string">Pennsylvania</street>
                  <state xsi:type="xsd:string">DC</state>
                  <city xsi:type="xsd:string">Washington</city>
                  <zip xsi:type="xsd:int">20502</zip>
                  <suffix xsi:type="xsd:string">NW</suffix>
                  <long xsi:type="xsd:float">-77.037684</long>
                  <type xsi:type="xsd:string">Ave</type>
                  <prefix xsi:type="xsd:string" />
                </item>
              </geo:s-gensym111>
            </namesp9:geocodeResponse>
          </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>"""
        */

        def Envelope = new XmlSlurper().parseText(soapResponse)
        println Envelope.Body.geocodeResponse.'s-gensym111'.item.long
        println Envelope.Body.geocodeResponse.'s-gensym111'.item.lat

        //since the array's name ('s-gensym111') changes with each request
        // we can deal with it generically as such:
        def coordinatesReply = []
        def itor = Envelope.Body.geocodeResponse.breadthFirst()
        while (itor.hasNext()) {
          def fragment = itor.next()
          if (fragment.name() == "item") {
            coordinatesReply += fragment.lat
            coordinatesReply += fragment.long
          }
        }

        println coordinatesReply
        counter++
        if (counter == numAddressesToTest) {
          stop()
          println "Stopping Receiver"
        }
      }
    }
  }
}

/**
 * This code can be used in a junit test method instead of main() to run a junit test.
 * It goes out to an internet site, geocoder.us, where one can input a home address and
 * receive a response back for the latitude and longitude of that address.   We use the groovy actors
 * framework (GPars) instead of the threads/locking/synchronized methodology of Java which
 * is very difficult to get right. (Common shared-memory multithreading causes more troubles than it solves.)
 * This test sends 3 such xml requests (to addresses 1, 2, 3 Hosmer St., Marlborough, MA)
 * as fast as possible in one actor and concurrently waits for the responses in another actor
 * (hence the reason we "start()" two actors). This is a simple test to make sure we've received
 * the number of responses that were sent but another idea would be to test for the maximum response
 * time across all of the responses received.
 */
class GeoTest {
  public static void main(args) {
    def numAddressesToTest = 3 // feel free to have fun and change this number
    def sender = new RequestSender(numAddressesToTest).start()
    def rc = new ResponseCatcher(sender, numAddressesToTest).start()
    //Actors provide a join() method to allow callers to wait for the actor to terminate.
    //The Groovy spread-dot operator comes in handy when joining multiple actors at a time.
    [sender, rc]*.join()
    // Now verify received the number of responses that were sent (using groovy's built in
    // unit testing assert mechanism.
    assert rc.getCounter() == numAddressesToTest
  }
}



