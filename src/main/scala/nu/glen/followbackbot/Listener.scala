package nu.glen.followbackbot

import twitter4j._

/**
 * A UserStreamListener that responds to the following events:
 *
 * onFollow: follows back
 * onStatus: replies if the responder produces a response to the status
 *
 * @param userId the userId of the bot
 * @param screenName the screenName of the bot
 * @param responder the Responder for status processing
 * @param socialGraph used to process follow/unfollow actions
 * @param twitter used to call the Twitter API
 */
class Listener(userId: Long,
               screenName: String,
               responder: Responder,
               socialGraph: SocialGraph,
               twitter: Twitter)
  extends UserStreamListener
  with SimpleLogger {

  protected[this] val retweetRegex = (""".*?\brt @""" + screenName.toLowerCase + "[: ].*").r

  protected[this] def isMe(user: User) = user.getId == userId

  /**
   * simple check should catch both old- and new-school RTs
   */
  protected[this] def isMyOwnRetweet(status: Status) =
    retweetRegex.findFirstIn(status.getText.toLowerCase).isDefined

  /**
   * reply to the status iff:
   * - the status's user is not the bot
   * - the status is not a retweet of a previous bot tweet
   * - the responder produces a response
   */
  override def onStatus(status: Status) {
    log.info(s"Got Status: @${status.getUser.getScreenName}: ${status.getText}")

    if (isMe(status.getUser)) {
      log.info(" Ignoring my own status")
    } else if (isMyOwnRetweet(status)) {
      log.info(" Ignoring a retweet of my own status")
    } else {
      responder(status) match {
        case Some(statusUpdate) =>
          tryAndLogResult(s" Replying (inReplyToStatusId = ${status.getId}) ${statusUpdate.getStatus}") {
            // only send the reply if the user still follows us
            if (socialGraph.checkOrUnfollow(status.getUser.getId).getOrElse(false)) {
              log.info(s" Tweeting: ${statusUpdate.getStatus}")
              try twitter.updateStatus(statusUpdate.inReplyToStatusId(status.getId))
              catch {
                case e: Exception =>
                  log.error("Twitter error", e)
                  throw e
              }
            }
          }

        case None => log.info(" Ignoring ineligible status")
      }
    }
  }

  /**
   * follow back if source isn't the bot
   */
  override def onFollow(source: User, followedUser: User) {
    log.info(s"Got follow notification: ${source.getScreenName}/${source.getId} " +
      s"-> ${followedUser.getScreenName}/${followedUser.getId}")

    if (isMe(source)) log.info(" Ignoring notification of my own actions")
    else socialGraph.follow(source.getId, Some(source.isProtected), true)
  }

  override def onBlock(source: User, blockedUser: User) = ()
  override def onDeletionNotice(directMessageId: Long, userId: Long) = ()
  override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = ()
  override def onDirectMessage(directMessage: DirectMessage) = ()
  override def onException(ex: Exception) = ()
  override def onFavorite(source: User, target: User, favoritedStatus: Status) = ()
  override def onFriendList(friendIds: Array[Long]) = ()
  override def onScrubGeo(userId: Long, upToStatusId: Long) = ()
  override def onStallWarning(warning: StallWarning) = ()
  override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = ()
  override def onUnblock(source: User, unblockedUser: User) = ()
  override def onUnfavorite(source: User, target: User, unfavoritedStatus: Status) = ()
  override def onUserListCreation(listOwner: User, list: UserList) = ()
  override def onUserListDeletion(listOwner: User, list: UserList) = ()
  override def onUserListMemberAddition(addedMember: User, listOwner: User, list: UserList) = ()
  override def onUserListMemberDeletion(deletedMember: User, listOwner: User, list: UserList) = ()
  override def onUserListSubscription(subscriber: User, listOwner: User, list: UserList) = ()
  override def onUserListUnsubscription(subscriber: User, listOwner: User, list: UserList) = ()
  override def onUserListUpdate(listOwner: User, list: UserList) = ()
  override def onUserProfileUpdate(updatedUser: User) = ()
}
