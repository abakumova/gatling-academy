package demostore.pageobjects

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object Catalog {

  val categoryFeeder = csv("data/categoryDetails.csv").random
  val productFeeder = jsonFile("data/productDetails.json").random

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