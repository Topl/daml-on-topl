package co.topl.broker

import cats.data.Kleisli

package object processors {

  type DAMLKleisli[F[_], A] = Kleisli[F, DAMLContext, A]

}
