package demostore

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class DemostoreSimulation extends Simulation {

  val domain = "demostore.gatling.io"

  val httpProtocol = http
    .baseUrl("http://" + domain)

  val categoryFeeder = csv("data/categoryDetails.csv").random
  val productFeeder = jsonFile("data/productDetails.json").random

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
    }

  }

  val scn = scenario("RecordedSimulation")
    .exec(CmsPages.homePage)
    .pause(2)
    .exec(CmsPages.aboutUsPage)
    .pause(2)
    .exec(Catalog.Category.view)
    .pause(2)
    .exec(Catalog.Product.view)
    .pause(2)
    .exec(http("Add product to the cart")
      .get("/cart/add/19"))
    .pause(2)
    .exec(http("View cart")
      .get("/cart/view"))
    .pause(2)
    .exec(http("Log in")
      .post("/login")
      .formParam("_csrf", "${csrfValue}")
      .formParam("username", "user1")
      .formParam("password", "pass"))
    .pause(2)
    .exec(http("Checkout")
      .get("/cart/checkout"))

  setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}