package com.linh.banking_ekyc.domain

import java.io.Serializable

data class ProfileModel(
    var profileName: String = "",
    var profileImage: String = "",
    var totalbalance: String = "",
    var income: String = "",
    var outcome: String = "",
    var banner: String = "",
    var friend: ArrayList<Friend> = arrayListOf(),
    var transaction: ArrayList<Transction> = arrayListOf()
) : Serializable

data class Friend(
    var imageUrl: String = "",
    var name: String = "",
) : Serializable

data class Transction(
    var imageUrl: String = "",
    var name: String = "",
    var data: String = "",
    var amount: String = "",
) : Serializable

