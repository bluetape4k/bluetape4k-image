ARG BASE_IMAGE
FROM ${BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY wheelhouse/ /wheelhouse/
COPY requirements.cpu.lock.txt /app/requirements.cpu.lock.txt
RUN python -m pip install --disable-pip-version-check --no-index --no-deps --require-hashes --find-links=/wheelhouse -r /app/requirements.cpu.lock.txt
COPY models/ /opt/bluetape4k/paddleocr/models/
COPY model-manifest.json /opt/bluetape4k/paddleocr/model-manifest.json
COPY ocr-pipeline.yaml /opt/bluetape4k/paddleocr/ocr-pipeline.yaml
COPY legal-inventory.json /opt/bluetape4k/paddleocr/legal-inventory.json
COPY service.py /app/service.py
COPY bin/bluetape4k-paddleocr-service /opt/bluetape4k/bin/bluetape4k-paddleocr-service
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/opt/bluetape4k/bin/bluetape4k-paddleocr-service"]
CMD ["--host", "127.0.0.1", "--port", "8080"]
