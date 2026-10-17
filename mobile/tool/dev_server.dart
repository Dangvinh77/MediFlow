import 'dart:io';

// Local static host + Gateway proxy: no backend CORS/config changes required.
Future<void> main(List<String> args) async {
  final port = args.isEmpty ? 4300 : int.parse(args[0]);
  final gateway = Uri.parse(
    args.length > 1 ? args[1] : 'http://127.0.0.1:8080',
  );
  if (!['http', 'https'].contains(gateway.scheme) ||
      gateway.userInfo.isNotEmpty ||
      gateway.host.isEmpty ||
      gateway.hasQuery ||
      gateway.hasFragment ||
      !['', '/'].contains(gateway.path) ||
      (gateway.port >= 8081 && gateway.port <= 8091) ||
      gateway.port == 8761) {
    throw ArgumentError(
      'Upstream phải là Gateway origin, ví dụ http://127.0.0.1:8080',
    );
  }
  final root = Directory('build/web').absolute;
  if (!File('${root.path}/index.html').existsSync()) {
    stderr.writeln('Chạy flutter build web trước; xem README.md.');
    exitCode = 1;
    return;
  }
  final client = HttpClient()..autoUncompress = false;
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
  stdout.writeln('MediFlow: http://localhost:$port — Gateway: $gateway');
  ProcessSignal.sigint.watch().listen((_) async {
    client.close(force: true);
    await server.close(force: true);
  });
  await for (final request in server) {
    _handle(request, root, gateway, client);
  }
}

Future<void> _handle(
  HttpRequest request,
  Directory root,
  Uri gateway,
  HttpClient client,
) async {
  try {
    if (request.uri.path.startsWith('/api/')) {
      final upstream = await client.openUrl(
        request.method,
        gateway.replace(
          path: request.uri.path,
          query: request.uri.hasQuery ? request.uri.query : null,
        ),
      );
      upstream.followRedirects = false;
      request.headers.forEach((name, values) {
        if (![
          'host',
          'connection',
          'transfer-encoding',
          'content-length',
        ].contains(name.toLowerCase())) {
          upstream.headers.set(name, values);
        }
      });
      await upstream.addStream(request);
      final response = await upstream.close().timeout(
        const Duration(seconds: 30),
      );
      request.response.statusCode = response.statusCode;
      response.headers.forEach((name, values) {
        if (!['connection', 'transfer-encoding'].contains(name.toLowerCase())) {
          request.response.headers.set(name, values);
        }
      });
      await request.response.addStream(response);
    } else if (!['GET', 'HEAD'].contains(request.method)) {
      request.response.statusCode = HttpStatus.methodNotAllowed;
    } else {
      final segments = request.uri.pathSegments;
      if (segments.any(
        (s) => s == '..' || s.contains('\\') || s.contains('/'),
      )) {
        request.response.statusCode = HttpStatus.badRequest;
      } else {
        final relative = request.uri.path == '/'
            ? 'index.html'
            : segments.join('/');
        final file = File('${root.path}/$relative');
        if (!await file.exists()) {
          request.response.statusCode = HttpStatus.notFound;
        } else {
          final extension = relative.split('.').last;
          final type = switch (extension) {
            'html' => 'text/html; charset=utf-8',
            'js' => 'application/javascript',
            'json' => 'application/json',
            'wasm' => 'application/wasm',
            'css' => 'text/css',
            'png' => 'image/png',
            'woff2' => 'font/woff2',
            _ => 'application/octet-stream',
          };
          request.response.headers.set('Content-Type', type);
          request.response.headers.set('Cache-Control', 'no-store');
          if (request.method != 'HEAD') {
            await request.response.addStream(file.openRead());
          }
        }
      }
    }
  } catch (_) {
    request.response.statusCode = HttpStatus.serviceUnavailable;
    request.response.headers.contentType = ContentType.json;
    request.response.write(
      '{"success":false,"data":null,"error":{"code":"GATEWAY_UNAVAILABLE",'
      '"message":"Gateway chưa khả dụng. Kiểm tra backend và thử lại."}}',
    );
  } finally {
    await request.response.close();
  }
}
