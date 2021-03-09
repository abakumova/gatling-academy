package demostore

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt
import scala.util.Random

class DemostoreSimulation extends Simulation {

  val domain = "demostore.gatling.io"

  val httpProtocol = http
    .baseUrl("http://" + domain)

  val categoryFeeder = csv("data/categoryDetails.csv").random
  val productFeeder = jsonFile("data/productDetails.json").random
  val loginFeeder = csv("data/loginDetails.csv").circular

  def userCount: Int = getProperty("USERS", "5").toInt
  def rampDuration: Int = getProperty("RAMP_DURATION", "10").toInt
  def testDuration: Int = getProperty("DURATION", "60").toInt

  val rnd = new Random()

  def randomString(length: Int): String = {
    rnd.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  val initSession = exec(flushCookieJar)
    .exec(session => session.set("randomNumber", rnd.nextInt))
    .exec(session => session.set("customerLoggedIn", false))
    .exec(session => session.set("cartTotal", 0.00))
    .exec(addCookie(Cookie("sessionId", randomString(10)).withDomain(domain)))
    .exec { session => println(session); session }

  object CmsPages {
    def homePage = {
      exec(http("Load Home page")
        .get("/")
        .check(status.is(200))
        .check(regex("<title>Gatling Demo-Store</title>").exists)
        .check(css("#_csrf", "content").saveAs("csrfValue")))
    }

    def aboutUsPage = {
      exec(http("Load About us page")
        .get("/about-us")
        .check(status.is(200))
        .check(substring("About Us")))
    }
  }

  object Catalog {

    object Category {
      def view = {
        feed(categoryFeeder)
          .exec(http("Load Category page - ${categoryName}")
            .get("/category/${categorySlug}")
            .check(status.is(200))
            .check(css("#CategoryName").is("${categoryName}")))
      }
    }

    object Product {
      def view = {
        feed(productFeeder)
          .exec(http("Load Product page - ${name}")
            .get("/product/${slug}")
            .check(status.is(200))
            .check(css("#ProductDescription").is("${description}"))
          )
      }

      def add = {
        exec(view).
          exec(http("Add Product to Cart")
            .get("/cart/add/${id}")
            .check(status.is(200))
            .check(substring("items in your cart"))
          )
          .exec(session => {
            val currentCartTotal = session("cartTotal").as[Double]
            val itemPrice = session("price").as[Double]
            session.set("cartTotal", (currentCartTotal + itemPrice))
          })
      }
    }

  }

  object Checkout {

    def viewCart = {
      doIf(session => !session("customerLoggedIn").as[Boolean]) {
        exec(Customer.login)
      }
        .exec(
          http("Load Cart Page")
            .get("/cart/view")
            .check(status.is(200))
            .check(css("#grandTotal").is("$$${cartTotal}"))
        )
    }

    def completeCheckout = {
      exec(
        http("Checkout Cart")
          .get("/cart/checkout")
          .check(status.is(200))
          .check(substring("Thanks for your order! See you soon!"))
      )
    }
  }

  object Customer {

    def login = {
      feed(loginFeeder)
        .exec(
          http("Load Login Page")
            .get("/login")
            .check(status.is(200))
            .check(substring("Username:"))
        )
        .exec { session => println(session); session }
        .exec(
          http("Customer Login Action")
            .post("/login")
            .formParam("_csrf", "${csrfValue}")
            .formParam("username", "${username}")
            .formParam("password", "${password}")
            .check(status.is(200))
        )
        .exec(session => session.set("customerLoggedIn", true))
        .exec { session => println(session); session }
    }
  }

  object UserJourneys {

    def minPause = 100.milliseconds

    def maxPause = 500.milliseconds

    def browseStore = {
      exec(initSession)
        .exec(CmsPages.homePage)
        .pause(maxPause)
        .exec(CmsPages.aboutUsPage)
        .pause(minPause, maxPause)
        .repeat(5) {
          exec(Catalog.Category.view)
            .pause(minPause, maxPause)
            .exec(Catalog.Product.view)
        }
    }

    def abandonCart = {
      exec(initSession)
        .exec(CmsPages.homePage)
        .pause(maxPause)
        .exec(Catalog.Category.view)
        .pause(minPause, maxPause)
        .exec(Catalog.Product.view)
        .pause(minPause, maxPause)
        .exec(Catalog.Product.add)
    }

    def completePurchase = {
      exec(initSession)
        .exec(CmsPages.homePage)
        .pause(maxPause)
        .exec(Catalog.Category.view)
        .pause(minPause, maxPause)
        .exec(Catalog.Product.view)
        .pause(minPause, maxPause)
        .exec(Catalog.Product.add)
        .pause(minPause, maxPause)
        .exec(Checkout.viewCart)
        .pause(minPause, maxPause)
        .exec(Checkout.completeCheckout)
    }
  }

  object Scenarios {

    def default = scenario("Default Load Test")
      .during(testDuration.seconds) {
        randomSwitch(
          75d -> exec(UserJourneys.browseStore),
          15d -> exec(UserJourneys.abandonCart),
          10d -> exec(UserJourneys.completePurchase)
        )
      }

    def highPurchase = scenario("High Purchase Load Test")
      .during(testDuration.seconds) {
        randomSwitch(
          25d -> exec(UserJourneys.browseStore),
          25d -> exec(UserJourneys.abandonCart),
          50d -> exec(UserJourneys.completePurchase)
        )
      }
  }

  val scn = scenario("RecordedSimulation")
    .exec(initSession)
    .exec(CmsPages.homePage)
    .pause(2)
    .exec(CmsPages.aboutUsPage)
    .pause(2)
    .exec(Catalog.Category.view)
    .pause(2)
    .exec(Catalog.Product.add)
    .pause(2)
    .exec(Checkout.viewCart)
    .pause(2)
    .exec(Checkout.completeCheckout)

  //Sim with 1 user
  //setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)

  //Regular Sim
  /*  setUp(
      scn.inject(
        atOnceUsers(3),
        nothingFor(5.seconds),
        rampUsers(10) during (20.seconds),
        nothingFor(10.seconds),
        constantUsersPerSec(1) during (20.seconds)
      ).protocols(httpProtocol)
    ) */

  //Closed Model Simulation
  /* setUp(scn.inject(
    constantConcurrentUsers(10) during (20.seconds),
    rampConcurrentUsers(10) to (20) during (20.seconds)
  )).protocols(httpProtocol) */

  //Throttle Simulation
 /* setUp(scn.inject(constantUsersPerSec(1) during (3.minutes))).protocols(httpProtocol).throttle(
    reachRps(10) in (30.seconds),
    holdFor(60.seconds),
    jumpToRps(20),
    holdFor(60.seconds)
  ).maxDuration(3.minutes) */

  setUp(Scenarios.default
    .inject(rampUsers(userCount) during (rampDuration.seconds))
    .protocols(httpProtocol))

  private def getProperty(propertyName: String, defaultValue: String) = {
    Option(System.getenv(propertyName))
      .orElse(Option(System.getProperty(propertyName)))
      .getOrElse(defaultValue)
  }
}