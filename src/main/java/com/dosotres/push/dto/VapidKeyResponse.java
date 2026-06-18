package com.dosotres.push.dto;

/** Clave pública VAPID (Base64 URL) que el frontend usa como applicationServerKey. */
public record VapidKeyResponse(String publicKey) {}
