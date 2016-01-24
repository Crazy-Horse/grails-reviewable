package com.sygmatech.grails.reviews

class ReviewLink {

    static belongsTo = [review:Review]

    Long reviewRef
    String type

    static constraints = {
        reviewRef min:0L
        type blank:false
    }

    static mapping = {
        cache true
    }
}
