# cryptography-kotlin finds its provider through ServiceLoader (decision 0010). Android's R8 keeps
# ServiceLoader implementations automatically, but pin them explicitly so a future minified release
# cannot drop the provider and fail at the first crypto call.
-keep class dev.whyoleg.cryptography.CryptographyProviderContainer
-keep class dev.whyoleg.cryptography.providers.jdk.JdkCryptographyProviderContainer
