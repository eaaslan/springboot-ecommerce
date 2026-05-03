# AWS Elastic Beanstalk deploy

End-to-end walkthrough for shipping the full stack to AWS.

> **Footprint reality check:** the Spring stack runs 13 services + Kafka +
> RabbitMQ. On a single EB instance you need **t3.large (8 GB RAM, ~$60/mo)** at
> minimum. For free-tier-friendly hosting, Oracle Cloud Ampere A1 (24 GB RAM
> ARM64) is the better option — covered in `docs/production-hardening.md`.

## Prerequisites

```bash
# AWS CLI + EB CLI
brew install awscli aws-elasticbeanstalk
aws configure                                  # access key, region, etc.

# GHCR images already published (CD pipeline ran on main)
docker pull ghcr.io/eaaslan/ecommerce-api-gateway:latest    # sanity check
```

## 1. Provision RDS + ElastiCache (one-time, ~10 min)

```bash
# RDS Postgres (db.t4g.micro is in free tier for 12 months)
aws rds create-db-instance \
  --db-instance-identifier ecommerce-postgres \
  --db-instance-class db.t4g.micro \
  --engine postgres \
  --engine-version 16 \
  --master-username ecommerce_admin \
  --master-user-password "$(openssl rand -hex 16)" \
  --allocated-storage 20 \
  --storage-type gp3 \
  --backup-retention-period 7 \
  --publicly-accessible

# ElastiCache Redis (cache.t4g.micro is the cheapest)
aws elasticache create-cache-cluster \
  --cache-cluster-id ecommerce-redis \
  --cache-node-type cache.t4g.micro \
  --engine redis \
  --num-cache-nodes 1
```

Note both endpoints — you'll set them as env vars in step 3.

## 2. Initialize Beanstalk app

From the repository root:

```bash
cd aws
eb init ecommerce \
  --region eu-central-1 \
  --platform "Docker running on 64bit Amazon Linux 2023"
eb create ecommerce-prod \
  --instance-type t3.large \
  --single                                     # no load balancer, save money
```

`eb create` provisions the EC2, deploys the bundle, and prints the public URL.

## 3. Set environment variables

```bash
eb setenv \
  POSTGRES_HOST=ecommerce-postgres.xxx.eu-central-1.rds.amazonaws.com \
  POSTGRES_USER=ecommerce_admin \
  POSTGRES_PASSWORD='<from step 1>' \
  REDIS_HOST=ecommerce-redis.xxx.cache.amazonaws.com \
  JWT_SECRET=$(openssl rand -hex 32) \
  IMAGE_TAG=latest \
  IYZICO_API_KEY=sandbox-... \
  IYZICO_SECRET_KEY=sandbox-...
```

The first deploy fires the `.ebextensions/01-init-databases.config` hook which
creates per-service databases in RDS.

## 4. Inbound rules

The RDS and ElastiCache security groups must allow inbound from the EB EC2's
security group:

```bash
EB_SG=$(aws ec2 describe-security-groups --filters "Name=tag:elasticbeanstalk:environment-name,Values=ecommerce-prod" --query 'SecurityGroups[0].GroupId' --output text)
RDS_SG=$(aws rds describe-db-instances --db-instance-identifier ecommerce-postgres --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp --port 5432 --source-group "$EB_SG"

REDIS_SG=$(aws elasticache describe-cache-clusters --cache-cluster-id ecommerce-redis --query 'CacheClusters[0].SecurityGroups[0].SecurityGroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id "$REDIS_SG" --protocol tcp --port 6379 --source-group "$EB_SG"
```

## 5. Deploy

```bash
eb deploy
```

Subsequent deploys: `git push origin main` → CD publishes new images to GHCR
with `IMAGE_TAG=<git-sha>`. To roll forward a specific commit on Beanstalk:

```bash
eb setenv IMAGE_TAG=<git-sha>
eb deploy
```

## 6. Verify

```bash
EB_URL=$(eb status | grep CNAME | awk '{print $2}')
curl "$EB_URL"                                 # → React SPA HTML
curl "$EB_URL/api/products?page=0&size=2"      # → catalog page
```

The ALB / single-instance EB terminates HTTP on port 80; nginx inside the
frontend container reverse-proxies `/api` to the gateway just like locally.

## 7. TLS

`eb create` with `--single` skips the load balancer. To add HTTPS:

- Issue a cert in ACM for your domain
- Reconfigure the env to use a load-balanced setup, OR
- Put CloudFront in front (cheapest, free SSL)

## 8. Tear down

```bash
eb terminate ecommerce-prod
aws rds delete-db-instance --db-instance-identifier ecommerce-postgres --skip-final-snapshot
aws elasticache delete-cache-cluster --cache-cluster-id ecommerce-redis
```

## Cost estimate (eu-central-1, on-demand)

| Resource | Spec | $/month |
|---|---|---|
| EB EC2 (t3.large single-instance) | 8 GB RAM, 2 vCPU | ~$60 |
| RDS db.t4g.micro | 1 GB RAM, 20 GB gp3 | free 12 mo, then ~$15 |
| ElastiCache cache.t4g.micro | 0.5 GB RAM | free 12 mo, then ~$15 |
| EBS volume | 30 GB | ~$3 |
| Data transfer | varies | ~$5 typical demo |
| **Total** | | **~$60-100/mo** |

vs. **Oracle Cloud Ampere A1 free tier: $0/mo** with 4 OCPU + 24 GB RAM. Pick
your poison.
